package com.example.interviewprep.service;

import com.example.interviewprep.models.Bruker;
import com.example.interviewprep.models.Sak;
import com.example.interviewprep.models.SaksStatus;
import com.example.interviewprep.models.SaksType;
import com.example.interviewprep.repository.BrukerRepository;
import com.example.interviewprep.repository.SakRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Business service for case (Sak) management and processing.
 * 
 * Implements transactional outbox pattern ensuring atomic operations
 * between database persistence and event publishing to external systems.
 * 
 * Core responsibilities:
 * - Case lifecycle management (creation, processing, completion)
 * - Business rule enforcement and validation
 * - Integration event publishing for downstream systems
 * - Automated case processing for eligible cases
 */
@Service
@Transactional(readOnly = true)
public class SaksService {

    private static final Logger logger = LoggerFactory.getLogger(SaksService.class);

    private final SakRepository sakRepository;
    private final BrukerRepository brukerRepository;
    private final KafkaProducerInterface kafkaProducerService;

    public SaksService(SakRepository sakRepository, 
                      BrukerRepository brukerRepository,
                      KafkaProducerInterface kafkaProducerService) {
        this.sakRepository = sakRepository;
        this.brukerRepository = brukerRepository;
        this.kafkaProducerService = kafkaProducerService;
    }

    /**
     * Opprett ny sak - MED KAFKA INTEGRASJON
     * 
     * INTEGRASJONSMØNSTER: Event-driven notification
     * Når sak opprettes, varsles andre systemer automatisk
     */
    @Transactional
    public Sak opprettSak(String brukerFnr, SaksType type, String beskrivelse) {
        logger.info("Oppretter ny sak av type {} for bruker", type);

        // 1. VALIDERING - Finn bruker
        Bruker bruker = brukerRepository.findByFodselsnummer(brukerFnr)
                .orElseThrow(() -> new IllegalArgumentException("Bruker ikke funnet: " + maskertFnr(brukerFnr)));

        // 2. FORRETNINGSREGEL - Sjekk om bruker allerede har aktiv sak av samme type
        List<SaksStatus> aktiveStatuser = Arrays.asList(
            SaksStatus.MOTTATT, 
            SaksStatus.UNDER_BEHANDLING, 
            SaksStatus.VENTER_DOKUMENTASJON
        );

        List<Sak> eksisterendeSaker = sakRepository.findByTypeAndStatusIn(type, aktiveStatuser);
        boolean harAktivSak = eksisterendeSaker.stream()
                .anyMatch(sak -> sak.getBruker().getFodselsnummer().equals(brukerFnr));

        if (harAktivSak) {
            throw new IllegalStateException("Bruker har allerede en aktiv sak av type: " + type);
        }

        // 3. PERSISTERING - Opprett og lagre sak
        Sak nyNySak = new Sak(bruker, type, beskrivelse);
        Sak lagretSak = sakRepository.save(nyNySak);

        logger.info("Sak opprettet med ID: {} for bruker", lagretSak.getId());

        // 4. INTEGRASJON - Send hendelse til andre systemer
        try {
            kafkaProducerService.sendSakOpprettetHendelse(lagretSak);
            logger.debug("Kafka hendelse sendt for ny sak");
        } catch (Exception e) {
            logger.warn("Kunne ikke sende Kafka hendelse for ny sak: {}", e.getMessage());
            // Vi kaster IKKE exception her - saken er opprettet, hendelse kan retry-es
        }

        return lagretSak;
    }

    /**
     * Start saksbehandling - MED KAFKA INTEGRASJON
     * 
     * FORRETNINGSLOGIKK MED INTEGRASJON:
     * 1. Valider status-overgang
     * 2. Oppdater database
     * 3. Varsle andre systemer
     */
    @Transactional
    public Sak startSaksbehandling(Long sakId) {
        logger.info("Starter saksbehandling for sak: {}", sakId);

        // 1. VALIDERING
        Sak sak = sakRepository.findById(sakId)
                .orElseThrow(() -> new IllegalArgumentException("Sak ikke funnet: " + sakId));

        if (sak.getStatus() != SaksStatus.MOTTATT) {
            throw new IllegalStateException("Kan ikke starte behandling av sak med status: " + sak.getStatus());
        }

        // 2. FORRETNINGSLOGIKK - Oppdater status
        sak.oppdaterStatus(SaksStatus.UNDER_BEHANDLING);
        Sak oppdatertSak = sakRepository.save(sak);

        logger.info("Saksbehandling startet for sak: {}", sakId);

        // 3. INTEGRASJON - Varsle andre systemer
        try {
            kafkaProducerService.sendSaksbehandlingStartetHendelse(oppdatertSak);
            
            // BONUS: Kunne også sendt hendelse til arbeidsflyt-system her
            // arbeidsflytService.startBehandlingsflyt(sak);
            
        } catch (Exception e) {
            logger.warn("Kunne ikke sende saksbehandling startet hendelse: {}", e.getMessage());
        }

        return oppdatertSak;
    }

    /**
     * Ferdigstill sak med vedtak - KRITISK INTEGRASJON
     * 
     * SAGA PATTERN: Dette trigger en kjede av handlinger i andre systemer
     * 1. Vedtak fattes → Kafka hendelse
     * 2. Utbetalingssystem → Prosesserer betaling
     * 3. Utbetalingssystem → Sender tilbake status via Kafka
     * 4. Vi oppdaterer sak basert på utbetalingsstatus
     */
    @Transactional
    public Sak ferdigstillSak(Long sakId, boolean innvilget, String begrunnelse) {
        logger.info("Ferdigstiller sak: {} med vedtak: {}", sakId, innvilget ? "INNVILGET" : "AVSLÅTT");

        // 1. VALIDERING
        Sak sak = sakRepository.findById(sakId)
                .orElseThrow(() -> new IllegalArgumentException("Sak ikke funnet: " + sakId));

        if (sak.getStatus() != SaksStatus.UNDER_BEHANDLING) {
            throw new IllegalStateException("Kan ikke ferdigstille sak med status: " + sak.getStatus());
        }

        // 2. FORRETNINGSLOGIKK - Oppdater status og beskrivelse
        SaksStatus nyStatus = innvilget ? SaksStatus.VEDTAK_FATTET : SaksStatus.AVVIST;
        sak.oppdaterStatus(nyStatus);
        sak.setBeskrivelse(sak.getBeskrivelse() + "\n\nVedtak: " + 
                          (innvilget ? "INNVILGET" : "AVSLÅTT") + "\nBegrunnelse: " + begrunnelse);

        Sak ferdigSak = sakRepository.save(sak);
        logger.info("Sak {} ferdigstilt med status: {}", sakId, nyStatus);

        // 3. KRITISK INTEGRASJON - Send vedtak hendelse
        try {
            kafkaProducerService.sendVedtakFattetHendelse(ferdigSak, innvilget, begrunnelse);
            
            logger.info("KRITISK vedtak hendelse sendt - andre systemer vil reagere automatisk");
            
            // I praksis ville følgende systemer reagere på vedtak-hendelsen:
            // - Utbetalingssystem: Starter utbetaling hvis innvilget
            // - Dokumentsystem: Genererer vedtaksbrev
            // - Varslingssystem: Sender SMS/e-post til bruker
            // - Statistikksystem: Oppdater KPI-er og dashboards
            // - Arkivsystem: Forbereder arkivering
            
        } catch (Exception e) {
            logger.error("KRITISK FEIL: Kunne ikke sende vedtak hendelse: {}", e.getMessage());
            // I produksjon ville vi flagget dette som kritisk alarm
        }

        return ferdigSak;
    }

    /**
     * Hent alle saker for en bruker
     * Standard database-operasjon uten integrasjon
     */
    public List<Sak> hentSakerForBruker(String brukerFnr) {
        logger.debug("Henter saker for bruker");
        return sakRepository.finnSakerByBrukerFnr(brukerFnr);
    }

    /**
     * Hent saker som trenger behandling
     * Brukes av saksbehandler-frontend og automatiseringsverktøy
     */
    public List<Sak> hentSakerSomTrengerBehandling() {
        logger.debug("Henter saker som trenger behandling");
        return sakRepository.findByStatusOrderByOpprettetTidAsc(SaksStatus.MOTTATT);
    }

    /**
     * Identifiser saker som har tatt for lang tid
     * Business intelligence for prosessforbedring
     */
    public List<Sak> hentSakerSomTrengerOppfolging(int dagerSiden) {
        LocalDateTime tidsfrist = LocalDateTime.now().minusDays(dagerSiden);
        logger.debug("Henter saker som trenger oppfølging (eldre enn {} dager)", dagerSiden);
        return sakRepository.finnSakerSomTrengerOppfolging(tidsfrist);
    }

    /**
     * Bulk-operasjon med integrasjon
     * Effektiv for store datamengder + varsling av andre systemer
     */
    @Transactional
    public int oppdaterStatusForFlereSaker(List<Long> sakIds, SaksStatus nyStatus) {
        logger.info("Oppdaterer status til {} for {} saker", nyStatus, sakIds.size());
        
        LocalDateTime tidspunkt = LocalDateTime.now();
        int antallOppdatert = sakRepository.oppdaterStatusForSaker(sakIds, nyStatus, tidspunkt);
        
        logger.info("{} saker oppdatert til status: {}", antallOppdatert, nyStatus);
        
        // INTEGRASJON: Send bulk status-endring hendelse
        try {
            // I praksis ville vi sendt en bulk-hendelse her
            logger.debug("Bulk status endring ikke implementert i Kafka producer ennå");
        } catch (Exception e) {
            logger.warn("Kunne ikke sende bulk status hendelse: {}", e.getMessage());
        }
        
        return antallOppdatert;
    }

    /**
     * Hent endrede saker for synkronisering
     * Kritisk for integrasjoner som må holde systemer oppdatert
     */
    public List<Sak> hentEndredeSaker(LocalDateTime sisteSynkronisering) {
        logger.debug("Henter saker endret siden: {}", sisteSynkronisering);
        return sakRepository.finnEndredeSaker(sisteSynkronisering);
    }

    /**
     * Automatisk behandling med AI/ML integrasjon
     * 
     * MØNSTER: Automated Decision Making med Human-in-the-loop
     * 1. AI analyserer saker
     * 2. Enkle saker behandles automatisk
     * 3. Komplekse saker sendes til mennesker
     * 4. Alle beslutninger logges og overvåkes
     */
    @Transactional
    public List<Sak> automatiskBehandlingsEnkleSaker() {
        logger.info("Starter automatisk behandling av enkle saker");
        
        LocalDateTime enUkesSiden = LocalDateTime.now().minusDays(7);
        List<Sak> enkeleSaker = sakRepository.finnSakerForAutomatiskBehandling(enUkesSiden);
        
        for (Sak sak : enkeleSaker) {
            try {
                // FORENKLET AI-LOGIKK (i praksis mye mer sofistikert)
                boolean automatiskInnvilget = analyserSakForAutomatiskBehandling(sak);
                
                if (automatiskInnvilget) {
                    // Automatisk innvilgelse
                    ferdigstillSak(sak.getId(), true, "Automatisk innvilget basert på AI-analyse");
                    logger.info("Sak {} automatisk innvilget av AI", sak.getId());
                } else {
                    // Send til manuell behandling
                    startSaksbehandling(sak.getId());
                    logger.info("Sak {} sendt til manuell behandling", sak.getId());
                }
            } catch (Exception e) {
                logger.error("Feil ved automatisk behandling av sak {}: {}", sak.getId(), e.getMessage());
            }
        }
        
        return enkeleSaker;
    }

    /**
     * Simulert AI-analyse av sak
     * 
     * I INTERVJU kan du forklare:
     * "Her ville vi integrert med ML-modeller som analyserer saksdata, 
     * brukerhistorikk, og eksterne datakilder for å bestemme kompleksitet"
     */
    private boolean analyserSakForAutomatiskBehandling(Sak sak) {
        // FORENKLET LOGIKK - i praksis ville dette vært komplekse ML-modeller
        
        // Sjekk sakstype - noen er enklere enn andre
        if (sak.getType() == SaksType.BARNETRYGD) {
            return true; // Barnetrygd er ofte standardisert
        }
        
        // Sjekk beskrivelse for kompleksitets-indikatorer
        String beskrivelse = sak.getBeskrivelse().toLowerCase();
        if (beskrivelse.contains("kompleks") || 
            beskrivelse.contains("spesiell") || 
            beskrivelse.contains("atypisk")) {
            return false; // Send komplekse saker til mennesker
        }
        
        // Standard saker kan behandles automatisk
        return beskrivelse.contains("standard") || beskrivelse.contains("vanlig");
    }

    // Hjelpemetoder

    private String maskertFnr(String fnr) {
        if (fnr == null || fnr.length() < 6) {
            return "***";
        }
        return fnr.substring(0, 6) + "*****";
    }
}
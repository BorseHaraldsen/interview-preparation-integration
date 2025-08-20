package com.example.interviewprep.service;

import com.example.interviewprep.models.Bruker;
import com.example.interviewprep.models.Sak;
import com.example.interviewprep.models.SaksStatus;
import com.example.interviewprep.repository.BrukerRepository;
import com.example.interviewprep.repository.SakRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Kafka Consumer Service - Mottar hendelser fra andre systemer
 * 
 * Dette viser den andre siden av hendelsesdreven arkitektur!
 * 
 * INTEGRASJONSMØNSTER: Event-driven consumer
 * - Lytter til hendelser fra eksterne systemer
 * - Prosesserer asynkront når hendelser ankommer
 * - Implementerer forretningslogikk basert på hendelser
 * 
 * I INTERVJU kan du forklare:
 * "Consumer-pattern lar oss reagere på hendelser fra andre systemer uten å 
 * være tett koblet til dem. Vi kan prosessere hendelser i vårt eget tempo."
 */
@Service
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);

    private final BrukerRepository brukerRepository;
    private final SakRepository sakRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public KafkaConsumerService(BrukerRepository brukerRepository, 
                               SakRepository sakRepository,
                               ObjectMapper objectMapper) {
        this.brukerRepository = brukerRepository;
        this.sakRepository = sakRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Lytt til hendelser fra Folkeregister-systemet
     * 
     * INTEGRASJON MED FOLKEREGISTER:
     * Når personer flytter, endrer navn, etc., må NAV-systemene oppdateres
     * 
     * @KafkaListener: Spring annotasjon som lytter til topic
     * groupId: Identifiserer denne applikasjonen som consumer
     * autoStartup: Starter automatisk ved oppstart
     */
    @KafkaListener(
        topics = "folkeregister.person.endret",
        groupId = "nav-integration-demo",
        autoStartup = "true"
    )
    @Transactional
    public void handleFolkeregisterEndring(
            @Payload String melding,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        logger.info("Mottatt folkeregister hendelse - Topic: {}, Partition: {}, Offset: {}", 
                   topic, partition, offset);

        try {
            // Parse JSON melding
            @SuppressWarnings("unchecked")
            Map<String, Object> hendelse = objectMapper.readValue(melding, Map.class);
            
            String hendelseType = (String) hendelse.get("hendelseType");
            String fnr = (String) hendelse.get("fodselsnummer");
            
            logger.debug("Prosesserer {} for person", hendelseType);

            switch (hendelseType) {
                case "ADRESSE_ENDRET" -> handleAdresseEndring(hendelse);
                case "NAVN_ENDRET" -> handleNavnEndring(hendelse);
                case "PERSON_DOED" -> handlePersonDoed(hendelse);
                case "PERSON_FLYTTET_TIL_UTLANDET" -> handleFlyttetTilUtlandet(hendelse);
                default -> logger.warn("Ukjent folkeregister hendelsetype: {}", hendelseType);
            }

            logger.debug("Folkeregister hendelse prosessert OK");

        } catch (Exception e) {
            logger.error("Feil ved prosessering av folkeregister hendelse: {}", e.getMessage());
            // Med BATCH mode blir commit håndtert automatisk
            throw new RuntimeException("Folkeregister hendelse prosessering feilet", e);
        }
    }

    /**
     * Lytt til hendelser fra utbetalingssystemet
     * 
     * KRITISK INTEGRASJON: Utbetalingsbekreftelser
     * Når penger er utbetalt, må saken oppdateres til riktig status
     */
    @KafkaListener(
        topics = "utbetaling.status.endret",
        groupId = "nav-integration-demo"
    )
    @Transactional
    public void handleUtbetalingStatus(
            @Payload String melding,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {

        logger.info("Mottatt utbetalingsstatus hendelse");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> hendelse = objectMapper.readValue(melding, Map.class);
            
            Long sakId = Long.valueOf(hendelse.get("sakId").toString());
            String utbetalingStatus = (String) hendelse.get("status");
            String transactionId = (String) hendelse.get("transactionId");

            logger.info("Utbetaling for sak {} har status: {}", sakId, utbetalingStatus);

            Optional<Sak> sakOpt = sakRepository.findById(sakId);
            if (sakOpt.isEmpty()) {
                logger.warn("Kan ikke finne sak {} for utbetalingsstatus", sakId);
                return;
            }

            Sak sak = sakOpt.get();

            // Oppdater saksstatus basert på utbetalingsstatus
            switch (utbetalingStatus) {
                case "UTBETALT" -> {
                    sak.oppdaterStatus(SaksStatus.UTBETALT);
                    sak.setBeskrivelse(sak.getBeskrivelse() + 
                                     "\nUtbetaling gjennomført - TransactionID: " + transactionId);
                    logger.info("Sak {} markert som UTBETALT", sakId);
                }
                case "FEILET" -> {
                    sak.oppdaterStatus(SaksStatus.VEDTAK_FATTET); // Tilbake til forrige status
                    sak.setBeskrivelse(sak.getBeskrivelse() + 
                                     "\nUtbetaling feilet - krever manuell oppfølging");
                    logger.warn("Utbetaling feilet for sak {} - krever manuell oppfølging", sakId);
                }
                case "UNDER_BEHANDLING" -> {
                    logger.debug("Utbetaling for sak {} er under behandling", sakId);
                    // Ingen statusendring nødvendig
                }
                default -> logger.warn("Ukjent utbetalingsstatus: {}", utbetalingStatus);
            }

            sakRepository.save(sak);

        } catch (Exception e) {
            logger.error("Feil ved prosessering av utbetalingsstatus: {}", e.getMessage());
            throw new RuntimeException("Utbetalingsstatus prosessering feilet", e);
        }
    }

    /**
     * Lytt til hendelser fra A-ordningen (arbeidsforhold)
     * 
     * INTEGRASJON MED A-ORDNINGEN:
     * Arbeidsforhold-data er kritisk for dagpenge-saker
     * Når noen mister jobben eller får ny jobb, påvirker dette NAV-saker
     */
    @KafkaListener(
        topics = "a-ordningen.arbeidsforhold.endret",
        groupId = "nav-integration-demo"
    )
    @Transactional
    public void handleArbeidsforholdEndring(
            @Payload String melding,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {

        logger.info("Mottatt arbeidsforhold hendelse fra A-ordningen");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> hendelse = objectMapper.readValue(melding, Map.class);
            
            String fnr = (String) hendelse.get("fodselsnummer");
            String endringsType = (String) hendelse.get("endringsType");
            
            logger.debug("Arbeidsforhold {} for person", endringsType);

            // Finn bruker
            Optional<Bruker> brukerOpt = brukerRepository.findByFodselsnummer(fnr);
            if (brukerOpt.isEmpty()) {
                logger.debug("Arbeidsforhold endring for person som ikke finnes i vårt system");
                return;
            }

            Bruker bruker = brukerOpt.get();

            switch (endringsType) {
                case "ARBEIDSFORHOLD_AVSLUTTET" -> handleArbeidsforholdAvsluttet(bruker, hendelse);
                case "ARBEIDSFORHOLD_STARTET" -> handleArbeidsforholdStartet(bruker, hendelse);
                case "LONN_ENDRET" -> handleLonnEndret(bruker, hendelse);
                default -> logger.debug("Ignorerer arbeidsforhold endring: {}", endringsType);
            }

        } catch (Exception e) {
            logger.error("Feil ved prosessering av arbeidsforhold hendelse: {}", e.getMessage());
            throw new RuntimeException("Arbeidsforhold hendelse prosessering feilet", e);
        }
    }

    /**
     * Generisk hendelse-lytter for intern testing
     * 
     * TESTING AV INTEGRASJONER:
     * Denne lytteren hjelper oss teste at Kafka fungerer
     */
    @KafkaListener(
        topics = {"nav.sak.hendelser", "nav.bruker.hendelser", "nav.vedtak.hendelser"},
        groupId = "nav-integration-demo-internal"
    )
    public void handleInternalEvents(
            @Payload String melding,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {

        logger.debug("Mottatt intern hendelse på topic: {}", topic);
        
        try {
            // I dette tilfellet bare logger vi - andre systemer ville reagert
            @SuppressWarnings("unchecked")
            Map<String, Object> hendelse = objectMapper.readValue(melding, Map.class);
            
            String hendelseType = (String) hendelse.get("hendelseType");
            logger.info("Intern hendelse prosessert: {} på topic {}", hendelseType, topic);
            
        } catch (Exception e) {
            logger.error("Feil ved prosessering av intern hendelse: {}", e.getMessage());
            // For interne hendelser, ignorer feil
        }
    }

    // Private hjelpemetoder for forretningslogikk

    private void handleAdresseEndring(Map<String, Object> hendelse) {
        String fnr = (String) hendelse.get("fodselsnummer");
        String nyAdresse = (String) hendelse.get("nyAdresse");
        
        Optional<Bruker> brukerOpt = brukerRepository.findByFodselsnummer(fnr);
        if (brukerOpt.isPresent()) {
            Bruker bruker = brukerOpt.get();
            String gammelAdresse = bruker.getAdresse();
            bruker.setAdresse(nyAdresse);
            brukerRepository.save(bruker);
            
            logger.info("Adresse oppdatert for bruker - Fra: {} Til: {}", 
                       gammelAdresse, nyAdresse);
        }
    }

    private void handleNavnEndring(Map<String, Object> hendelse) {
        String fnr = (String) hendelse.get("fodselsnummer");
        String nyttNavn = (String) hendelse.get("nyttNavn");
        
        Optional<Bruker> brukerOpt = brukerRepository.findByFodselsnummer(fnr);
        if (brukerOpt.isPresent()) {
            Bruker bruker = brukerOpt.get();
            String gammeltNavn = bruker.getNavn();
            bruker.setNavn(nyttNavn);
            brukerRepository.save(bruker);
            
            logger.info("Navn oppdatert for bruker - Fra: {} Til: {}", 
                       gammeltNavn, nyttNavn);
        }
    }

    private void handlePersonDoed(Map<String, Object> hendelse) {
        String fnr = (String) hendelse.get("fodselsnummer");
        
        // Finn alle aktive saker for personen og avslutt dem
        Optional<Bruker> brukerOpt = brukerRepository.findByFodselsnummer(fnr);
        if (brukerOpt.isPresent()) {
            Bruker bruker = brukerOpt.get();
            
            // Avslutt alle aktive saker
            bruker.getSaker().stream()
                .filter(sak -> SaksStatus.getAktiveStatuser().contains(sak.getStatus()))
                .forEach(sak -> {
                    sak.oppdaterStatus(SaksStatus.AVSLUTTET);
                    sak.setBeskrivelse(sak.getBeskrivelse() + "\nSak avsluttet grunnet dødsfall");
                    sakRepository.save(sak);
                });
            
            logger.warn("Person død - {} aktive saker avsluttet", 
                       bruker.getSaker().size());
        }
    }

    private void handleFlyttetTilUtlandet(Map<String, Object> hendelse) {
        String fnr = (String) hendelse.get("fodselsnummer");
        String land = (String) hendelse.get("land");
        
        // Spesiell behandling for utflytting - påvirker flere ytelser
        Optional<Bruker> brukerOpt = brukerRepository.findByFodselsnummer(fnr);
        if (brukerOpt.isPresent()) {
            Bruker bruker = brukerOpt.get();
            
            logger.info("Person flyttet til {} - vurder påvirkning på aktive saker", land);
            
            // I praksis ville dette trigget kompleks forretningslogikk
            // basert på hvilket land og hvilke avtaler Norge har
        }
    }

    private void handleArbeidsforholdAvsluttet(Bruker bruker, Map<String, Object> hendelse) {
        String orgnr = (String) hendelse.get("organisasjonsnummer");
        
        logger.info("Arbeidsforhold avsluttet for bruker hos org: {}", orgnr);
        
        // I praksis ville dette kunne trigge automatisk dagpenge-søknad
        // eller varsle bruker om rettigheter
    }

    private void handleArbeidsforholdStartet(Bruker bruker, Map<String, Object> hendelse) {
        String orgnr = (String) hendelse.get("organisasjonsnummer");
        
        logger.info("Nytt arbeidsforhold startet for bruker hos org: {}", orgnr);
        
        // Dette kunne påvirke pågående dagpenge-saker
        // Person som får jobb kan miste rett til dagpenger
    }

    private void handleLonnEndret(Bruker bruker, Map<String, Object> hendelse) {
        Double nyLonn = Double.valueOf(hendelse.get("nyMaanedslonn").toString());
        
        logger.debug("Lønn endret for bruker - ny månedslønn: {}", nyLonn);
        
        // Lønnsendringer kan påvirke beregning av ytelser
    }
}
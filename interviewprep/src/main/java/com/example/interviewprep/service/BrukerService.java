package com.example.interviewprep.service;

import com.example.interviewprep.models.Bruker;
import com.example.interviewprep.repository.BrukerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Business service for managing user (Bruker) operations.
 * 
 * Handles user lifecycle management, validation, and coordination
 * between data access and external system integration layers.
 * Implements transactional boundaries and business rule enforcement.
 */
@Service
@Transactional(readOnly = true) // Default til read-only for performance
public class BrukerService {

    private static final Logger logger = LoggerFactory.getLogger(BrukerService.class);

    private final BrukerRepository brukerRepository;

    /**
     * Constructor-based dependency injection for testability and explicit dependencies.
     */
    @Autowired
    public BrukerService(BrukerRepository brukerRepository) {
        this.brukerRepository = brukerRepository;
    }

    /**
     * Creates a new user with business rule validation.
     * Enforces unique national ID constraint and publishes integration events.
     * 
     * @param fodselsnummer Norwegian national identification number (11 digits)
     * @param navn Full name of the user
     * @param adresse Complete address information
     * @return Created user entity with generated ID
     * @throws IllegalArgumentException if user already exists or validation fails
     */
    @Transactional
    public Bruker opprettBruker(String fodselsnummer, String navn, String adresse) {
        logger.info("Oppretter ny bruker med fnr: {}", maskertFnr(fodselsnummer));

        // Forretningsregel: Sjekk om bruker allerede eksisterer
        if (brukerRepository.existsByFodselsnummer(fodselsnummer)) {
            logger.warn("Forsøk på å opprette duplikat bruker: {}", maskertFnr(fodselsnummer));
            throw new IllegalArgumentException("Bruker med dette fødselsnummeret eksisterer allerede");
        }

        // Validering av fødselsnummer (forenklet)
        if (!erGyldigFodselsnummer(fodselsnummer)) {
            throw new IllegalArgumentException("Ugyldig fødselsnummer");
        }

        Bruker bruker = new Bruker(fodselsnummer, navn, adresse);
        Bruker lagretBruker = brukerRepository.save(bruker);

        logger.info("Bruker opprettet med ID: {}", lagretBruker.getId());
        return lagretBruker;
    }

    /**
     * Finn bruker basert på fødselsnummer
     * Typisk integrasjons-operasjon mellom NAV-systemer
     */
    public Optional<Bruker> finnBrukerByFnr(String fodselsnummer) {
        logger.debug("Søker etter bruker med fnr: {}", maskertFnr(fodselsnummer));
        return brukerRepository.findByFodselsnummer(fodselsnummer);
    }

    /**
     * Hent alle brukere (med forsiktighet - kan være mange!)
     * I praksis ville man brukt paginering
     */
    public List<Bruker> hentAlleBrukere() {
        logger.debug("Henter alle brukere");
        return brukerRepository.findAll();
    }

    /**
     * Oppdater bruker informasjon
     * Viser hvordan vi håndterer endringer i integrasjoner
     */
    @Transactional
    public Bruker oppdaterBruker(Long id, String navn, String adresse) {
        logger.info("Oppdaterer bruker med ID: {}", id);

        Bruker bruker = brukerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bruker ikke funnet: " + id));

        // Oppdater kun hvis verdier er forskjellige (optimalisering)
        boolean endret = false;
        if (navn != null && !navn.equals(bruker.getNavn())) {
            bruker.setNavn(navn);
            endret = true;
        }
        if (adresse != null && !adresse.equals(bruker.getAdresse())) {
            bruker.setAdresse(adresse);
            endret = true;
        }

        if (endret) {
            Bruker oppdatertBruker = brukerRepository.save(bruker);
            logger.info("Bruker {} oppdatert", id);
            return oppdatertBruker;
        } else {
            logger.debug("Ingen endringer for bruker {}", id);
            return bruker;
        }
    }

    /**
     * Søk brukere basert på navn
     * Viser søkefunksjonalitet som ofte trengs i integrasjoner
     */
    public List<Bruker> sokBrukere(String navnSok) {
        logger.debug("Søker brukere med navn som inneholder: {}", navnSok);
        return brukerRepository.finnBrukereByNavnSoek(navnSok);
    }

    /**
     * Hent statistikk over nye brukere
     * Typisk for rapporter og dashboards
     */
    public List<Bruker> hentNyeBrukere(LocalDateTime fraDato) {
        logger.debug("Henter brukere registrert etter: {}", fraDato);
        return brukerRepository.findByOpprettetTidAfter(fraDato);
    }

    /**
     * Hent brukere med aktive saker
     * Viser integrasjon mellom ulike domener
     */
    public List<Bruker> hentBrukereMedAktiveSaker() {
        logger.debug("Henter brukere med aktive saker");
        return brukerRepository.finnBrukereMedAktiveSaker();
    }

    /**
     * Slett bruker (kun for testing - i produksjon ville man "soft delete")
     */
    @Transactional
    public void slettBruker(Long id) {
        logger.warn("Sletter bruker med ID: {}", id);
        if (!brukerRepository.existsById(id)) {
            throw new IllegalArgumentException("Bruker ikke funnet: " + id);
        }
        brukerRepository.deleteById(id);
    }

    // Hjelpemetoder

    /**
     * Forenklet validering av fødselsnummer
     * I praksis ville man brukt mer sofistikert validering
     */
    private boolean erGyldigFodselsnummer(String fnr) {
        if (fnr == null || fnr.length() != 11) {
            return false;
        }
        // Sjekk at alle tegn er siffer
        return fnr.matches("\\d{11}");
    }

    /**
     * Masker fødselsnummer for logging (personvern)
     * Viktig i NAV-systemer hvor persondata må beskyttes
     */
    private String maskertFnr(String fnr) {
        if (fnr == null || fnr.length() < 6) {
            return "***";
        }
        return fnr.substring(0, 6) + "*****";
    }
}
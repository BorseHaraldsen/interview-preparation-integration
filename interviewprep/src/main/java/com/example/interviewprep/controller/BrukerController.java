package com.example.interviewprep.controller;

import com.example.interviewprep.models.Bruker;
import com.example.interviewprep.service.BrukerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import java.util.List;

/**
 * REST Controller for Bruker operasjoner
 * 
 * Dette er API-et som andre NAV-systemer bruker for å integrere med brukerdata
 * Viser hvordan vi eksponerer tjenester for integrasjon
 * 
 * @RestController: Kombinerer @Controller og @ResponseBody
 * @RequestMapping: Base URL for alle endpoints i denne controlleren
 * @Validated: Aktiverer validering på controller-nivå
 */
@RestController
@RequestMapping("/api/v1/brukere")
@Validated
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:8080"})
public class BrukerController {

    private static final Logger logger = LoggerFactory.getLogger(BrukerController.class);

    private final BrukerService brukerService;

    @Autowired
    public BrukerController(BrukerService brukerService) {
        this.brukerService = brukerService;
    }

    /**
     * Hent alle brukere
     * GET /api/v1/brukere
     * 
     * Brukes av andre systemer for å få oversikt over brukere
     */
    @GetMapping
    public ResponseEntity<List<Bruker>> hentAlleBrukere() {
        logger.info("API kall: Hent alle brukere");
        
        List<Bruker> brukere = brukerService.hentAlleBrukere();
        logger.debug("Returnerer {} brukere", brukere.size());
        
        return ResponseEntity.ok(brukere);
    }

    /**
     * Hent bruker basert på fødselsnummer
     * GET /api/v1/brukere/fnr/{fodselsnummer}
     * 
     * Kritisk integrasjons-endpoint som andre NAV-systemer bruker
     */
    @GetMapping("/fnr/{fodselsnummer}")
    public ResponseEntity<Bruker> hentBrukerByFnr(
            @PathVariable 
            @Pattern(regexp = "\\d{11}", message = "Fødselsnummer må være 11 siffer")
            String fodselsnummer) {
        
        logger.info("API kall: Hent bruker by fnr");
        
        return brukerService.finnBrukerByFnr(fodselsnummer)
                .map(bruker -> {
                    logger.debug("Bruker funnet");
                    return ResponseEntity.ok(bruker);
                })
                .orElseGet(() -> {
                    logger.warn("Bruker ikke funnet");
                    return ResponseEntity.notFound().build();
                });
    }

    /**
     * Hent bruker basert på ID
     * GET /api/v1/brukere/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Bruker> hentBrukerById(@PathVariable Long id) {
        logger.info("API kall: Hent bruker med ID: {}", id);
        
        try {
            // For enkelhet bruker vi finnBrukerByFnr, men i praksis ville vi hatt en finnById metode
            List<Bruker> alleBrukere = brukerService.hentAlleBrukere();
            return alleBrukere.stream()
                    .filter(b -> b.getId().equals(id))
                    .findFirst()
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            logger.error("Feil ved henting av bruker {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Opprett ny bruker
     * POST /api/v1/brukere
     * 
     * Request body eksempel:
     * {
     *   "fodselsnummer": "12345678901",
     *   "navn": "Test Testesen", 
     *   "adresse": "Testveien 1, 0001 Oslo"
     * }
     */
    @PostMapping
    public ResponseEntity<Bruker> opprettBruker(@Valid @RequestBody NyBrukerRequest request) {
        logger.info("API kall: Opprett ny bruker");
        
        try {
            Bruker nyBruker = brukerService.opprettBruker(
                    request.getFodselsnummer(),
                    request.getNavn(),
                    request.getAdresse()
            );
            
            logger.info("Ny bruker opprettet med ID: {}", nyBruker.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(nyBruker);
            
        } catch (IllegalArgumentException e) {
            logger.warn("Ugyldig data ved opprettelse av bruker: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Feil ved opprettelse av bruker: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Oppdater bruker informasjon
     * PUT /api/v1/brukere/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<Bruker> oppdaterBruker(
            @PathVariable Long id,
            @Valid @RequestBody OppdaterBrukerRequest request) {
        
        logger.info("API kall: Oppdater bruker {}", id);
        
        try {
            Bruker oppdatertBruker = brukerService.oppdaterBruker(
                    id,
                    request.getNavn(),
                    request.getAdresse()
            );
            
            logger.info("Bruker {} oppdatert", id);
            return ResponseEntity.ok(oppdatertBruker);
            
        } catch (IllegalArgumentException e) {
            logger.warn("Bruker ikke funnet: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Feil ved oppdatering av bruker {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Søk brukere basert på navn
     * GET /api/v1/brukere/sok?navn={navn}
     * 
     * Brukes for å finne brukere når man ikke har fødselsnummer
     */
    @GetMapping("/sok")
    public ResponseEntity<List<Bruker>> sokBrukere(
            @RequestParam @NotBlank(message = "Søkenavn kan ikke være tomt") String navn) {
        
        logger.info("API kall: Søk brukere med navn: {}", navn);
        
        List<Bruker> brukere = brukerService.sokBrukere(navn);
        logger.debug("Fant {} brukere som matcher søket", brukere.size());
        
        return ResponseEntity.ok(brukere);
    }

    /**
     * Hent nye brukere siden en gitt dato
     * GET /api/v1/brukere/nye?siden=2024-01-01T00:00:00
     * 
     * Brukes for synkronisering mellom systemer
     */
    @GetMapping("/nye")
    public ResponseEntity<List<Bruker>> hentNyeBrukere(
            @RequestParam String siden) {
        
        logger.info("API kall: Hent nye brukere siden: {}", siden);
        
        try {
            LocalDateTime fraDato = LocalDateTime.parse(siden);
            List<Bruker> nyeBrukere = brukerService.hentNyeBrukere(fraDato);
            
            logger.debug("Fant {} nye brukere siden {}", nyeBrukere.size(), siden);
            return ResponseEntity.ok(nyeBrukere);
            
        } catch (Exception e) {
            logger.warn("Ugyldig datoformat: {}", siden);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Hent brukere med aktive saker
     * GET /api/v1/brukere/aktive-saker
     * 
     * Integrasjon mellom bruker- og saksystem
     */
    @GetMapping("/aktive-saker")
    public ResponseEntity<List<Bruker>> hentBrukereMedAktiveSaker() {
        logger.info("API kall: Hent brukere med aktive saker");
        
        List<Bruker> brukere = brukerService.hentBrukereMedAktiveSaker();
        logger.debug("Fant {} brukere med aktive saker", brukere.size());
        
        return ResponseEntity.ok(brukere);
    }

    /**
     * Slett bruker (kun for testing)
     * DELETE /api/v1/brukere/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> slettBruker(@PathVariable Long id) {
        logger.warn("API kall: Slett bruker {}", id);
        
        try {
            brukerService.slettBruker(id);
            logger.info("Bruker {} slettet", id);
            return ResponseEntity.noContent().build();
            
        } catch (IllegalArgumentException e) {
            logger.warn("Bruker ikke funnet for sletting: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Feil ved sletting av bruker {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Health check endpoint
     * GET /api/v1/brukere/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Bruker API er tilgjengelig");
    }
}

/**
 * DTO klasser for request bodies
 * Separerer API kontrakt fra intern datamodell
 */
class NyBrukerRequest {
    @NotBlank(message = "Fødselsnummer er påkrevd")
    @Pattern(regexp = "\\d{11}", message = "Fødselsnummer må være 11 siffer")
    private String fodselsnummer;
    
    @NotBlank(message = "Navn er påkrevd")
    private String navn;
    
    @NotBlank(message = "Adresse er påkrevd")
    private String adresse;

    // Getters og setters
    public String getFodselsnummer() { return fodselsnummer; }
    public void setFodselsnummer(String fodselsnummer) { this.fodselsnummer = fodselsnummer; }
    public String getNavn() { return navn; }
    public void setNavn(String navn) { this.navn = navn; }
    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }
}

class OppdaterBrukerRequest {
    private String navn;
    private String adresse;

    // Getters og setters
    public String getNavn() { return navn; }
    public void setNavn(String navn) { this.navn = navn; }
    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }
}
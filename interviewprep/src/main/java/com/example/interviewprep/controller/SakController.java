package com.example.interviewprep.controller;

import com.example.interviewprep.models.Sak;
import com.example.interviewprep.models.SaksStatus;
import com.example.interviewprep.models.SaksType;
import com.example.interviewprep.service.SaksService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Sak operasjoner
 * 
 * Dette er et kritisk API som håndterer saksbehandling i NAV
 * Viser komplekse integrasjonsmønstre og forretningslogikk
 */
@RestController
@RequestMapping("/api/v1/saker")
@Validated
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:8080"})
public class SakController {

    private static final Logger logger = LoggerFactory.getLogger(SakController.class);

    private final SaksService saksService;

    @Autowired
    public SakController(SaksService saksService) {
        this.saksService = saksService;
    }

    /**
     * Opprett ny sak
     * POST /api/v1/saker
     * 
     * Dette er ofte første integrasjonspunkt når bruker søker om tjenester
     * Request body eksempel:
     * {
     *   "brukerFnr": "12345678901",
     *   "type": "DAGPENGER",
     *   "beskrivelse": "Søknad om dagpenger etter permittering"
     * }
     */
    @PostMapping
    public ResponseEntity<SakResponse> opprettSak(@Valid @RequestBody OpprettSakRequest request) {
        logger.info("API kall: Opprett ny sak av type {} for bruker", request.getType());

        try {
            Sak nySak = saksService.opprettSak(
                    request.getBrukerFnr(),
                    request.getType(),
                    request.getBeskrivelse()
            );

            SakResponse response = tilSakResponse(nySak);
            logger.info("Ny sak opprettet med ID: {}", nySak.getId());

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.warn("Ugyldig data ved opprettelse av sak: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            logger.warn("Forretningsregel brutt: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            logger.error("Feil ved opprettelse av sak: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Hent alle saker for en bruker
     * GET /api/v1/saker/bruker/{fodselsnummer}
     * 
     * Kritisk integrasjon - andre systemer trenger å se brukers saker
     */
    @GetMapping("/bruker/{fodselsnummer}")
    public ResponseEntity<List<SakResponse>> hentSakerForBruker(
            @PathVariable 
            @Pattern(regexp = "\\d{11}", message = "Fødselsnummer må være 11 siffer")
            String fodselsnummer) {

        logger.info("API kall: Hent saker for bruker");

        List<Sak> saker = saksService.hentSakerForBruker(fodselsnummer);
        List<SakResponse> response = saker.stream()
                .map(this::tilSakResponse)
                .toList();

        logger.debug("Returnerer {} saker for bruker", saker.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Start saksbehandling
     * PUT /api/v1/saker/{sakId}/start-behandling
     * 
     * Trigger for å starte saksbehandlingsprosessen
     * Vil normalt trigge andre integrasjoner (arbeidslister, varsler, etc.)
     */
    @PutMapping("/{sakId}/start-behandling")
    public ResponseEntity<SakResponse> startSaksbehandling(@PathVariable Long sakId) {
        logger.info("API kall: Start saksbehandling for sak {}", sakId);

        try {
            Sak oppdatertSak = saksService.startSaksbehandling(sakId);
            SakResponse response = tilSakResponse(oppdatertSak);

            logger.info("Saksbehandling startet for sak {}", sakId);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("Sak ikke funnet: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            logger.warn("Ugyldig status-overgang: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            logger.error("Feil ved start av saksbehandling: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Ferdigstill sak med vedtak
     * PUT /api/v1/saker/{sakId}/ferdigstill
     * 
     * Kritisk endpoint som ferdigstiller saker og trigger utbetalinger
     * Request body eksempel:
     * {
     *   "innvilget": true,
     *   "begrunnelse": "Alle vilkår oppfylt"
     * }
     */
    @PutMapping("/{sakId}/ferdigstill")
    public ResponseEntity<SakResponse> ferdigstillSak(
            @PathVariable Long sakId,
            @Valid @RequestBody FerdigstillSakRequest request) {

        logger.info("API kall: Ferdigstill sak {} med vedtak: {}", 
                   sakId, request.isInnvilget() ? "INNVILGET" : "AVSLÅTT");

        try {
            Sak ferdigSak = saksService.ferdigstillSak(
                    sakId,
                    request.isInnvilget(),
                    request.getBegrunnelse()
            );

            SakResponse response = tilSakResponse(ferdigSak);
            logger.info("Sak {} ferdigstilt", sakId);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.warn("Sak ikke funnet: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            logger.warn("Ugyldig status for ferdigstilling: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            logger.error("Feil ved ferdigstilling av sak: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Hent arbeidsliste - saker som trenger behandling
     * GET /api/v1/saker/arbeidsliste
     * 
     * Brukes av saksbehandler-frontend og automatiseringsverktøy
     */
    @GetMapping("/arbeidsliste")
    public ResponseEntity<List<SakResponse>> hentArbeidsliste() {
        logger.info("API kall: Hent arbeidsliste");

        List<Sak> saker = saksService.hentSakerSomTrengerBehandling();
        List<SakResponse> response = saker.stream()
                .map(this::tilSakResponse)
                .toList();

        logger.debug("Returnerer {} saker i arbeidslisten", saker.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Hent saker som trenger oppfølging
     * GET /api/v1/saker/oppfolging?dager=30
     * 
     * Business intelligence: Identifiser saker som tar for lang tid
     */
    @GetMapping("/oppfolging")
    public ResponseEntity<List<SakResponse>> hentSakerSomTrengerOppfolging(
            @RequestParam(defaultValue = "30") int dager) {

        logger.info("API kall: Hent saker som trenger oppfølging (eldre enn {} dager)", dager);

        List<Sak> saker = saksService.hentSakerSomTrengerOppfolging(dager);
        List<SakResponse> response = saker.stream()
                .map(this::tilSakResponse)
                .toList();

        logger.debug("Fant {} saker som trenger oppfølging", saker.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Bulk oppdatering av saksstatus
     * PUT /api/v1/saker/bulk-oppdater
     * 
     * Effektiv integrasjon for å oppdatere mange saker samtidig
     * Request body eksempel:
     * {
     *   "sakIds": [1, 2, 3],
     *   "nyStatus": "UNDER_BEHANDLING"
     * }
     */
    @PutMapping("/bulk-oppdater")
    public ResponseEntity<Map<String, Object>> bulkOppdaterStatus(
            @Valid @RequestBody BulkOppdaterRequest request) {

        logger.info("API kall: Bulk oppdater {} saker til status {}", 
                   request.getSakIds().size(), request.getNyStatus());

        try {
            int antallOppdatert = saksService.oppdaterStatusForFlereSaker(
                    request.getSakIds(),
                    request.getNyStatus()
            );

            Map<String, Object> response = Map.of(
                    "antallOppdatert", antallOppdatert,
                    "nyStatus", request.getNyStatus(),
                    "tidspunkt", LocalDateTime.now()
            );

            logger.info("Bulk oppdatering fullført: {} saker oppdatert", antallOppdatert);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Feil ved bulk oppdatering: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Hent endrede saker for synkronisering
     * GET /api/v1/saker/endret-siden?tidspunkt=2024-01-01T00:00:00
     * 
     * Kritisk for integrasjoner som må holde systemer synkronisert
     */
    @GetMapping("/endret-siden")
    public ResponseEntity<List<SakResponse>> hentEndredeSaker(
            @RequestParam String tidspunkt) {

        logger.info("API kall: Hent saker endret siden {}", tidspunkt);

        try {
            LocalDateTime sisteSynk = LocalDateTime.parse(tidspunkt);
            List<Sak> saker = saksService.hentEndredeSaker(sisteSynk);
            List<SakResponse> response = saker.stream()
                    .map(this::tilSakResponse)
                    .toList();

            logger.debug("Fant {} endrede saker siden {}", saker.size(), tidspunkt);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.warn("Ugyldig tidspunkt format: {}", tidspunkt);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Trigger automatisk behandling
     * POST /api/v1/saker/automatisk-behandling
     * 
     * Endpoint for automatiseringsverktøy og scheduled jobs
     */
    @PostMapping("/automatisk-behandling")
    public ResponseEntity<Map<String, Object>> automatiskBehandling() {
        logger.info("API kall: Start automatisk behandling");

        try {
            List<Sak> behandledeSaker = saksService.automatiskBehandlingsEnkleSaker();

            Map<String, Object> response = Map.of(
                    "antallBehandlet", behandledeSaker.size(),
                    "tidspunkt", LocalDateTime.now(),
                    "status", "FULLFØRT"
            );

            logger.info("Automatisk behandling fullført: {} saker behandlet", behandledeSaker.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Feil ved automatisk behandling: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Health check
     * GET /api/v1/saker/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Sak API er tilgjengelig");
    }

    // Hjelpemetoder

    /**
     * Konverter Sak entitet til SakResponse DTO
     * Separerer intern datamodell fra API kontrakt
     */
    private SakResponse tilSakResponse(Sak sak) {
        return new SakResponse(
                sak.getId(),
                sak.getBruker().getFodselsnummer(),
                sak.getBruker().getNavn(),
                sak.getType(),
                sak.getStatus(),
                sak.getBeskrivelse(),
                sak.getOpprettetTid(),
                sak.getSistEndret()
        );
    }
}

// DTO klasser for API kontrakter

class OpprettSakRequest {
    @NotBlank(message = "Bruker fødselsnummer er påkrevd")
    @Pattern(regexp = "\\d{11}", message = "Fødselsnummer må være 11 siffer")
    private String brukerFnr;

    @NotNull(message = "Sakstype er påkrevd")
    private SaksType type;

    @NotBlank(message = "Beskrivelse er påkrevd")
    private String beskrivelse;

    // Getters og setters
    public String getBrukerFnr() { return brukerFnr; }
    public void setBrukerFnr(String brukerFnr) { this.brukerFnr = brukerFnr; }
    public SaksType getType() { return type; }
    public void setType(SaksType type) { this.type = type; }
    public String getBeskrivelse() { return beskrivelse; }
    public void setBeskrivelse(String beskrivelse) { this.beskrivelse = beskrivelse; }
}

class FerdigstillSakRequest {
    private boolean innvilget;
    
    @NotBlank(message = "Begrunnelse er påkrevd")
    private String begrunnelse;

    // Getters og setters
    public boolean isInnvilget() { return innvilget; }
    public void setInnvilget(boolean innvilget) { this.innvilget = innvilget; }
    public String getBegrunnelse() { return begrunnelse; }
    public void setBegrunnelse(String begrunnelse) { this.begrunnelse = begrunnelse; }
}

class BulkOppdaterRequest {
    @NotNull(message = "Sak IDer er påkrevd")
    private List<Long> sakIds;

    @NotNull(message = "Ny status er påkrevd")
    private SaksStatus nyStatus;

    // Getters og setters
    public List<Long> getSakIds() { return sakIds; }
    public void setSakIds(List<Long> sakIds) { this.sakIds = sakIds; }
    public SaksStatus getNyStatus() { return nyStatus; }
    public void setNyStatus(SaksStatus nyStatus) { this.nyStatus = nyStatus; }
}

class SakResponse {
    private final Long id;
    private final String brukerFnr;
    private final String brukerNavn;
    private final SaksType type;
    private final SaksStatus status;
    private final String beskrivelse;
    private final LocalDateTime opprettetTid;
    private final LocalDateTime sistEndret;

    public SakResponse(Long id, String brukerFnr, String brukerNavn, SaksType type, 
                      SaksStatus status, String beskrivelse, LocalDateTime opprettetTid, 
                      LocalDateTime sistEndret) {
        this.id = id;
        this.brukerFnr = maskertFnr(brukerFnr);
        this.brukerNavn = brukerNavn;
        this.type = type;
        this.status = status;
        this.beskrivelse = beskrivelse;
        this.opprettetTid = opprettetTid;
        this.sistEndret = sistEndret;
    }

    private String maskertFnr(String fnr) {
        if (fnr == null || fnr.length() < 6) return "***";
        return fnr.substring(0, 6) + "*****";
    }

    // Getters
    public Long getId() { return id; }
    public String getBrukerFnr() { return brukerFnr; }
    public String getBrukerNavn() { return brukerNavn; }
    public SaksType getType() { return type; }
    public SaksStatus getStatus() { return status; }
    public String getBeskrivelse() { return beskrivelse; }
    public LocalDateTime getOpprettetTid() { return opprettetTid; }
    public LocalDateTime getSistEndret() { return sistEndret; }
}
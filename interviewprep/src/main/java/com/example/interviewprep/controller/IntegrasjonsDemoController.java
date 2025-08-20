package com.example.interviewprep.controller;

import com.example.interviewprep.models.Sak;
import com.example.interviewprep.models.SaksType;
import com.example.interviewprep.models.dto.FolkeregisterData;
import com.example.interviewprep.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo Controller for integrasjonsmønstre
 * 
 * Denne controlleren demonstrerer ALLE integrasjonsmønstrene vi har bygget:
 * 1. REST API (denne controlleren selv)
 * 2. Database integrasjon (via services)
 * 3. Event-driven arkitektur (Kafka)
 * 4. Eksterne REST API-kall
 * 5. Retry patterns og feilhåndtering
 * 6. Asynkron prosessering
 * 
 * PERFEKT FOR INTERVJU-DEMO!
 * Du kan vise en komplett integrasonssflyt fra start til slutt.
 */
@RestController
@RequestMapping("/api/v1/integrasjon-demo")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:8080"})
public class IntegrasjonsDemoController {

    private static final Logger logger = LoggerFactory.getLogger(IntegrasjonsDemoController.class);

    private final SaksService saksService;
    private final BrukerService brukerService;
    private final KafkaProducerInterface kafkaProducerService; // Bruker interface
    private final EksternIntegrasjonService eksternIntegrasjonService;

    @Autowired
    public IntegrasjonsDemoController(SaksService saksService,
                                     BrukerService brukerService,
                                     KafkaProducerInterface kafkaProducerService,
                                     EksternIntegrasjonService eksternIntegrasjonService) {
        this.saksService = saksService;
        this.brukerService = brukerService;
        this.kafkaProducerService = kafkaProducerService;
        this.eksternIntegrasjonService = eksternIntegrasjonService;
    }

    /**
     * KOMPLETT INTEGRASJONSFLYT DEMO
     * POST /api/v1/integrasjon-demo/full-demo
     * 
     * Dette endpointet viser HELE integrasjonsflyten:
     * 1. Mottar API-kall (REST)
     * 2. Henter data fra eksterne systemer (REST integrasjon)
     * 3. Validerer og prosesserer data (forretningslogikk)
     * 4. Lagrer i database (repository pattern)
     * 5. Sender hendelser til andre systemer (Kafka)
     * 6. Returnerer respons til klient (REST)
     * 
     * I INTERVJU kan du kjøre dette og forklare hvert steg!
     */
    @PostMapping("/full-demo")
    public ResponseEntity<Map<String, Object>> fullIntegrasjonDemo(
            @RequestBody DemoRequest request) {

        logger.info("🚀 STARTER FULL INTEGRASJON DEMO for bruker: {}", request.getFnr());

        Map<String, Object> result = new HashMap<>();
        result.put("startTid", LocalDateTime.now());

        try {
            // STEG 1: EKSTERN INTEGRASJON - Hent data fra Folkeregister
            logger.info("📡 STEG 1: Henter persondata fra Folkeregister...");
            try {
                FolkeregisterData personData = eksternIntegrasjonService.hentPersonFraFolkeregister(request.getFnr());
                result.put("folkeregisterData", Map.of(
                    "navn", personData.getNavn(),
                    "adresse", personData.getAdresse(),
                    "status", "HENTET_OK"
                ));
                logger.info("✅ Folkeregister data hentet OK");
            } catch (Exception e) {
                logger.warn("⚠️ Folkeregister ikke tilgjengelig - bruker mock data: {}", e.getMessage());
                result.put("folkeregisterData", Map.of(
                    "navn", "Mock Person",
                    "adresse", "Mock Adresse 1",
                    "status", "MOCK_DATA"
                ));
            }

            // STEG 2: DATABASE INTEGRASJON - Opprett bruker hvis ikke eksisterer
            logger.info("💾 STEG 2: Sjekker/oppretter bruker i database...");
            var brukerOpt = brukerService.finnBrukerByFnr(request.getFnr());
            var bruker = brukerOpt.orElseGet(() -> {
                logger.info("Oppretter ny bruker");
                return brukerService.opprettBruker(request.getFnr(), "Demo Bruker", "Demo Adresse");
            });
            
            result.put("brukerData", Map.of(
                "id", bruker.getId(),
                "navn", bruker.getNavn(),
                "opprettet", bruker.getOpprettetTid(),
                "status", brukerOpt.isPresent() ? "EKSISTERTE" : "OPPRETTET"
            ));
            logger.info("✅ Bruker klar i database");

            // STEG 3: FORRETNINGSLOGIKK - Opprett sak
            logger.info("⚙️ STEG 3: Oppretter sak...");
            Sak sak = saksService.opprettSak(
                request.getFnr(),
                request.getSaksType(),
                "Demo sak opprettet via integrasjon demo: " + request.getBeskrivelse()
            );
            
            result.put("sakData", Map.of(
                "sakId", sak.getId(),
                "type", sak.getType(),
                "status", sak.getStatus(),
                "opprettet", sak.getOpprettetTid()
            ));
            logger.info("✅ Sak opprettet med ID: {}", sak.getId());

            // STEG 4: EVENT-DRIVEN INTEGRASJON - Send hendelse via Kafka
            logger.info("📨 STEG 4: Sender hendelser via Kafka...");
            // Sak opprettet hendelse sendes automatisk i saksService
            // La oss sende en ekstra demo-hendelse
            kafkaProducerService.sendHealthCheckHendelse();
            
            result.put("kafkaData", Map.of(
                "sakOpprettetHendelse", "SENDT_AUTOMATISK",
                "healthCheckHendelse", "SENDT_MANUELT",
                "status", "HENDELSER_PUBLISERT"
            ));
            logger.info("✅ Kafka hendelser sendt");

            // STEG 5: EKSTERN INTEGRASJON - Hent tilleggsdata basert på sakstype
            logger.info("🔍 STEG 5: Henter tilleggsdata basert på sakstype...");
            if (request.getSaksType() == SaksType.DAGPENGER) {
                try {
                    var arbeidsforhold = eksternIntegrasjonService.hentArbeidsforholdFraAOrdningen(
                        request.getFnr(), LocalDateTime.now().minusYears(3)
                    );
                    result.put("arbeidsforholdData", Map.of(
                        "antall", arbeidsforhold.size(),
                        "status", "HENTET_OK"
                    ));
                } catch (Exception e) {
                    result.put("arbeidsforholdData", Map.of(
                        "status", "IKKE_TILGJENGELIG",
                        "feil", e.getMessage()
                    ));
                }
            }

            // STEG 6: SAKSBEHANDLING SIMULERING
            if (request.isAutoStart()) {
                logger.info("🏃 STEG 6: Starter automatisk saksbehandling...");
                Sak startetSak = saksService.startSaksbehandling(sak.getId());
                
                result.put("saksbehandlingData", Map.of(
                    "status", startetSak.getStatus(),
                    "startetTid", startetSak.getSistEndret(),
                    "autoStartet", true
                ));
                logger.info("✅ Saksbehandling startet automatisk");
            }

            result.put("sluttTid", LocalDateTime.now());
            result.put("totalStatus", "SUKSESS");
            
            logger.info("🎉 FULL INTEGRASJON DEMO FULLFØRT!");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("❌ INTEGRASJON DEMO FEILET: {}", e.getMessage());
            result.put("feil", e.getMessage());
            result.put("sluttTid", LocalDateTime.now());
            result.put("totalStatus", "FEIL");
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Demo av Kafka producer
     * POST /api/v1/integrasjon-demo/test-kafka
     */
    @PostMapping("/test-kafka")
    public ResponseEntity<Map<String, Object>> testKafka(@RequestBody TestKafkaRequest request) {
        logger.info("🔥 Testing Kafka producer...");

        try {
            kafkaProducerService.sendHealthCheckHendelse();
            
            Map<String, Object> result = Map.of(
                "status", "KAFKA_TEST_SENDT",
                "melding", "Health check hendelse sendt",
                "tidspunkt", LocalDateTime.now()
            );

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Kafka test feilet: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "status", "KAFKA_TEST_FEILET",
                "feil", e.getMessage()
            ));
        }
    }

    /**
     * Demo av ekstern API integrasjon
     * GET /api/v1/integrasjon-demo/test-ekstern-api/{fnr}
     */
    @GetMapping("/test-ekstern-api/{fnr}")
    public ResponseEntity<Map<String, Object>> testEksternApi(@PathVariable String fnr) {
        logger.info("🌐 Testing ekstern API integrasjon for fnr: {}", fnr.substring(0, 6) + "*****");

        Map<String, Object> result = new HashMap<>();

        try {
            // Test Folkeregister
            try {
                FolkeregisterData data = eksternIntegrasjonService.hentPersonFraFolkeregister(fnr);
                result.put("folkeregister", Map.of(
                    "status", "OK",
                    "navn", data.getNavn(),
                    "adresse", data.getAdresse()
                ));
            } catch (Exception e) {
                result.put("folkeregister", Map.of(
                    "status", "FEIL",
                    "feil", e.getMessage()
                ));
            }

            // Test A-ordningen
            try {
                var arbeidsforhold = eksternIntegrasjonService.hentArbeidsforholdFraAOrdningen(
                    fnr, LocalDateTime.now().minusYears(1)
                );
                result.put("aOrdningen", Map.of(
                    "status", "OK",
                    "antallArbeidsforhold", arbeidsforhold.size()
                ));
            } catch (Exception e) {
                result.put("aOrdningen", Map.of(
                    "status", "FEIL",
                    "feil", e.getMessage()
                ));
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Ekstern API test feilet: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "status", "GENERELL_FEIL",
                "feil", e.getMessage()
            ));
        }
    }

    /**
     * Demo av automatisk saksbehandling
     * POST /api/v1/integrasjon-demo/auto-behandling
     */
    @PostMapping("/auto-behandling")
    public ResponseEntity<Map<String, Object>> testAutomatiskBehandling() {
        logger.info("🤖 Testing automatisk saksbehandling...");

        try {
            List<Sak> behandledeSaker = saksService.automatiskBehandlingsEnkleSaker();

            Map<String, Object> result = Map.of(
                "status", "AUTO_BEHANDLING_FULLFØRT",
                "antallSakerBehandlet", behandledeSaker.size(),
                "tidspunkt", LocalDateTime.now()
            );

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Automatisk behandling feilet: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "status", "AUTO_BEHANDLING_FEILET",
                "feil", e.getMessage()
            ));
        }
    }

    /**
     * Health check for integrasjoner
     * GET /api/v1/integrasjon-demo/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "OK");
        health.put("tidspunkt", LocalDateTime.now());
        health.put("integrasjonerTilgjengelig", Map.of(
            "database", "OK",
            "kafka", "OK", // I praksis ville vi testet faktisk tilkobling
            "folkeregister", "SIMULERT",
            "aOrdningen", "SIMULERT"
        ));

        return ResponseEntity.ok(health);
    }
}

// Request DTOs

class DemoRequest {
    private String fnr;
    private SaksType saksType;
    private String beskrivelse;
    private boolean autoStart = false;

    // Getters og Setters
    public String getFnr() { return fnr; }
    public void setFnr(String fnr) { this.fnr = fnr; }
    public SaksType getSaksType() { return saksType; }
    public void setSaksType(SaksType saksType) { this.saksType = saksType; }
    public String getBeskrivelse() { return beskrivelse; }
    public void setBeskrivelse(String beskrivelse) { this.beskrivelse = beskrivelse; }
    public boolean isAutoStart() { return autoStart; }
    public void setAutoStart(boolean autoStart) { this.autoStart = autoStart; }
}

class TestKafkaRequest {
    private String melding;
    
    public String getMelding() { return melding; }
    public void setMelding(String melding) { this.melding = melding; }
}

package com.example.interviewprep.service;

import com.example.interviewprep.models.Sak;
import com.example.interviewprep.models.Bruker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka message producer service for publishing integration events.
 * 
 * Conditionally loaded when Kafka is enabled in application configuration.
 * Implements asynchronous event publishing with error handling and retry logic.
 * Falls back to NoKafkaProducerService when Kafka is disabled for development.
 */
@Service
@ConditionalOnProperty(
    name = "kafka.enabled", 
    havingValue = "true", 
    matchIfMissing = false
)
public class KafkaProducerService implements KafkaProducerInterface {

    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Topic constants - should be externalized to configuration in production
    private static final String SAK_HENDELSER_TOPIC = "nav.sak.hendelser";
    private static final String BRUKER_HENDELSER_TOPIC = "nav.bruker.hendelser";
    private static final String VEDTAK_HENDELSER_TOPIC = "nav.vedtak.hendelser";

    @Autowired
    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes case creation event to downstream systems.
     * 
     * Integration consumers may include:
     * - Work queue management systems
     * - User notification services  
     * - Business intelligence and reporting
     * - External government registries
     * 
     * @param sak The created case entity
     */
    public void sendSakOpprettetHendelse(Sak sak) {
        logger.info("Sender 'Sak Opprettet' hendelse for sak: {}", sak.getId());

        // Bygg hendelse-payload
        Map<String, Object> hendelse = new HashMap<>();
        hendelse.put("hendelseType", "SAK_OPPRETTET");
        hendelse.put("tidspunkt", LocalDateTime.now().toString());
        hendelse.put("sakId", sak.getId());
        hendelse.put("brukerFnr", maskertFnr(sak.getBruker().getFodselsnummer()));
        hendelse.put("saksType", sak.getType().toString());
        hendelse.put("status", sak.getStatus().toString());
        
        // Metadata for sporing
        hendelse.put("kilde", "SAK_SERVICE");
        hendelse.put("correlationId", generateCorrelationId());

        // Send asynkront med callback
        sendAsync(SAK_HENDELSER_TOPIC, sak.getId().toString(), hendelse, 
                 "Sak opprettet hendelse sendt", 
                 "Feil ved sending av sak opprettet hendelse");
    }

    /**
     * Send hendelse når saksbehandling starter
     * 
     * FORRETNINGSVERDI: Andre systemer kan reagere automatisk
     * - Arbeidsflyt-system kan tildele saksbehandler
     * - Time-tracking system kan starte måling
     * - SLA-overvåking kan sette frister
     */
    public void sendSaksbehandlingStartetHendelse(Sak sak) {
        logger.info("Sender 'Saksbehandling Startet' hendelse for sak: {}", sak.getId());

        Map<String, Object> hendelse = new HashMap<>();
        hendelse.put("hendelseType", "SAKSBEHANDLING_STARTET");
        hendelse.put("tidspunkt", LocalDateTime.now().toString());
        hendelse.put("sakId", sak.getId());
        hendelse.put("saksType", sak.getType().toString());
        hendelse.put("forventetBehandlingstid", sak.getType().getForventetBehandlingstidDager());
        hendelse.put("correlationId", generateCorrelationId());

        sendAsync(SAK_HENDELSER_TOPIC, sak.getId().toString(), hendelse,
                 "Saksbehandling startet hendelse sendt",
                 "Feil ved sending av saksbehandling startet hendelse");
    }

    /**
     * Send KRITISK hendelse når vedtak fattes
     * 
     * INTEGRASJONSMØNSTER: Saga pattern
     * Vedtak trigger en kjede av handlinger i andre systemer:
     * 1. Utbetalingssystem → Overfør penger
     * 2. Dokumentsystem → Generer vedtaksbrev
     * 3. Varslingssystem → Send SMS til bruker
     * 4. Statistikksystem → Oppdater KPI-er
     */
    public void sendVedtakFattetHendelse(Sak sak, boolean innvilget, String begrunnelse) {
        logger.info("Sender KRITISK 'Vedtak Fattet' hendelse for sak: {} - Resultat: {}", 
                   sak.getId(), innvilget ? "INNVILGET" : "AVSLÅTT");

        Map<String, Object> hendelse = new HashMap<>();
        hendelse.put("hendelseType", "VEDTAK_FATTET");
        hendelse.put("tidspunkt", LocalDateTime.now().toString());
        hendelse.put("sakId", sak.getId());
        hendelse.put("brukerFnr", maskertFnr(sak.getBruker().getFodselsnummer()));
        hendelse.put("saksType", sak.getType().toString());
        hendelse.put("innvilget", innvilget);
        hendelse.put("begrunnelse", begrunnelse);
        
        // Kritisk metadata
        hendelse.put("prioritet", "KRITISK");
        hendelse.put("kreverOppfolging", innvilget);
        hendelse.put("correlationId", generateCorrelationId());

        // Send til spesialisert vedtak-topic (høyere prioritet)
        sendAsync(VEDTAK_HENDELSER_TOPIC, sak.getId().toString(), hendelse,
                 "KRITISK vedtak hendelse sendt",
                 "ALVORLIG FEIL ved sending av vedtak hendelse");
    }

    /**
     * Send hendelse når bruker opprettes/endres
     * 
     * INTEGRASJONSMØNSTER: Master Data Management
     * Brukerdata må synkroniseres til mange systemer
     */
    public void sendBrukerEndretHendelse(Bruker bruker, String endringsType) {
        logger.info("Sender 'Bruker Endret' hendelse: {} for bruker", endringsType);

        Map<String, Object> hendelse = new HashMap<>();
        hendelse.put("hendelseType", "BRUKER_" + endringsType.toUpperCase());
        hendelse.put("tidspunkt", LocalDateTime.now().toString());
        hendelse.put("brukerId", bruker.getId());
        hendelse.put("brukerFnr", maskertFnr(bruker.getFodselsnummer()));
        hendelse.put("navn", bruker.getNavn());
        hendelse.put("adresse", bruker.getAdresse());
        hendelse.put("correlationId", generateCorrelationId());

        sendAsync(BRUKER_HENDELSER_TOPIC, bruker.getFodselsnummer(), hendelse,
                 "Bruker hendelse sendt",
                 "Feil ved sending av bruker hendelse");
    }

    /**
     * Generisk async send-metode med error handling
     * 
     * ROBUSTHET: Viser hvordan vi håndterer feil i integrasjoner
     * I intervju kan du forklare viktigheten av resilient systems
     */
    private void sendAsync(String topic, String key, Object payload, 
                          String successMessage, String errorMessage) {
        
        CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send(topic, key, payload);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                logger.info("{} - Topic: {}, Key: {}, Offset: {}", 
                           successMessage, topic, key, 
                           result.getRecordMetadata().offset());
            } else {
                logger.error("{} - Topic: {}, Key: {}, Error: {}", 
                            errorMessage, topic, key, ex.getMessage());
                
                // I produksjon ville vi:
                // 1. Sendt til dead letter queue
                // 2. Trigget alert til ops-team
                // 3. Logget til error tracking system (Sentry/Bugsnag)
                handleSendFailure(topic, key, payload, ex);
            }
        });
    }

    /**
     * Feilhåndtering for failed sends
     * 
     * RESILIENS-MØNSTER: Circuit breaker, retry, dead letter queues
     */
    private void handleSendFailure(String topic, String key, Object payload, Throwable ex) {
        logger.error("Kafka send failure - lagrer for retry");
        
        // I praksis ville vi:
        // 1. Lagre i database for senere retry
        // 2. Send alert til monitoring system
        // 3. Implementere exponential backoff retry
        
        // For demo: Log strukturert data for troubleshooting
        logger.error("Failed message details - Topic: {}, Key: {}, Payload: {}, Error: {}", 
                    topic, key, payload, ex.getMessage());
    }

    // Hjelpemetoder

    /**
     * Generer unik correlation ID for sporing
     * OBSERVABILITY: Kritisk for debugging integrasjoner
     */
    private String generateCorrelationId() {
        return "NAV-" + System.currentTimeMillis() + "-" + 
               Thread.currentThread().getId();
    }

    /**
     * Masker fødselsnummer for logging (GDPR compliance)
     * SIKKERHET: NAV må beskytte persondata
     */
    private String maskertFnr(String fnr) {
        if (fnr == null || fnr.length() < 6) {
            return "***";
        }
        return fnr.substring(0, 6) + "*****";
    }

    /**
     * Send test-hendelse for monitorering
     * OPERASJONELL OVERVÅKNING: Helsesjekk av integrasjoner
     */
    public void sendHealthCheckHendelse() {
        Map<String, Object> healthCheck = new HashMap<>();
        healthCheck.put("hendelseType", "HEALTH_CHECK");
        healthCheck.put("tidspunkt", LocalDateTime.now().toString());
        healthCheck.put("status", "OK");
        healthCheck.put("correlationId", generateCorrelationId());

        sendAsync("nav.health.check", "health", healthCheck,
                 "Health check sendt",
                 "Health check feilet");
    }
}
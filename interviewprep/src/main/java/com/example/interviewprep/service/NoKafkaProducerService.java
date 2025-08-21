package com.example.interviewprep.service;

import com.example.interviewprep.models.Sak;
import com.example.interviewprep.models.Bruker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Mock implementation of Kafka producer service for development environments.
 * 
 * Provides simulation of event publishing when Kafka infrastructure is unavailable.
 * Logs intended message publishing for debugging and demonstration purposes.
 * Enables application functionality without external message broker dependencies.
 */
@Service
@ConditionalOnProperty(
    name = "kafka.enabled", 
    havingValue = "false", 
    matchIfMissing = true
)
public class NoKafkaProducerService implements KafkaProducerInterface {

    private static final Logger logger = LoggerFactory.getLogger(NoKafkaProducerService.class);

    @Override
    public void sendSakOpprettetHendelse(Sak sak) {
        logger.info("[SIMULATION] Case creation event would be published to Kafka for case: {}", sak.getId());
        logger.debug("Event payload would include: caseId={}, type={}, status={}", 
                    sak.getId(), sak.getType(), sak.getStatus());
    }

    @Override
    public void sendSaksbehandlingStartetHendelse(Sak sak) {
        logger.info("[SIMULATION] Case processing started event would be published to Kafka for case: {}", sak.getId());
    }

    @Override
    public void sendVedtakFattetHendelse(Sak sak, boolean innvilget, String begrunnelse) {
        logger.info("[SIMULATION] Critical decision event would be published to Kafka for case: {} - Result: {}", 
                   sak.getId(), innvilget ? "APPROVED" : "REJECTED");
        logger.info("In production, this would trigger payment systems, document generation, and user notifications");
    }

    @Override
    public void sendBrukerEndretHendelse(Bruker bruker, String endringsType) {
        logger.info("[SIMULATION] User {} event would be published to Kafka", endringsType);
    }

    @Override
    public void sendHealthCheckHendelse() {
        logger.info("[SIMULATION] Health check event would be published to Kafka");
        logger.info("Status: Kafka producer simulation operational");
    }

    @Override
    public void sendGenericEvent(String topic, Object eventData) {
        logger.info("[SIMULATION] Generic event would be published to Kafka topic: {}", topic);
        logger.debug("[SIMULATION] Event data: {}", eventData);
        logger.info("In production, this would be published to real Kafka topic for downstream processing");
    }
}
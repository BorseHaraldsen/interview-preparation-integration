package com.example.interviewprep.service;

import com.example.interviewprep.models.Sak;
import com.example.interviewprep.models.Bruker;

/**
 * Interface for Kafka Producer Services
 * 
 * Dette sikrer at både ekte Kafka service og mock service 
 * implementerer samme metoder.
 * 
 * INTEGRASJONSMØNSTER: Strategy Pattern
 * - Lar oss bytte mellom implementasjoner basert på miljø
 * - Mock for utvikling/demo, ekte for produksjon
 * 
 * I INTERVJU kan du forklare:
 * "Interface lar oss ha forskjellige implementasjoner for forskjellige miljøer.
 * Demo-mode bruker logging, prod bruker faktisk Kafka."
 */
public interface KafkaProducerInterface {
    
    /**
     * Send hendelse når ny sak opprettes
     */
    void sendSakOpprettetHendelse(Sak sak);
    
    /**
     * Send hendelse når saksbehandling starter
     */
    void sendSaksbehandlingStartetHendelse(Sak sak);
    
    /**
     * Send kritisk hendelse når vedtak fattes
     */
    void sendVedtakFattetHendelse(Sak sak, boolean innvilget, String begrunnelse);
    
    /**
     * Send hendelse når brukerdata endres
     */
    void sendBrukerEndretHendelse(Bruker bruker, String endringsType);
    
    /**
     * Send health check hendelse for monitorering
     */
    void sendHealthCheckHendelse();
}
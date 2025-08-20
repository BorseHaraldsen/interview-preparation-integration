package com.example.interviewprep.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Litt usikker da jeg har gjort rabbitMQ før
 * Kafka konfigurasjon for hendelsesdreven arkitektur
 *
 * Denne klassen viser hvordan ulike systemer kan kommunisere asynkront via hendelser
 *
 * @EnableKafka: Aktiverer Kafka støtte i Spring
 * @Configuration: Markerer dette som en konfigurasjonskLasse
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    /**
     * Produserer konfigurasjon for å sende hendelser
     * Brukes når vårt system sender meldinger til andre systemer
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // Kafka server konfigurasjon
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        // Serialisering: Hvordan vi konverterer Java objekter til bytes
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Pålitelighet: Vent på bekreftelse fra alle replicas
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        
        // Retry konfigurasjon for robusthet
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        
        // Idempotens: Unngå duplikate meldinger
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        // Performance tuning
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * KafkaTemplate for å sende meldinger
     * Dette er hovedverktøyet for å publisere hendelser
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Consumer konfigurasjon for å motta hendelser
     * Brukes når vårt system lytter til hendelser fra andre systemer
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // Kafka server konfigurasjon
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        
        // Deserialisering: Hvordan vi konverterer bytes til Java objekter
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        
        // Start fra beginning hvis ingen offset er lagret
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        // Automatisk commit av offsets
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        configProps.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 1000);
        
        // Trusted packages for JSON deserialisering (sikkerhet)
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "no.nav.integration.model");
        
        // Performance og sikkerhet
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        configProps.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        configProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);

        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * Listener container factory for @KafkaListener
     * Konfigurerer hvordan vi lytter til meldinger
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
                new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory());
        
        // Concurrency: Antall parallelle konsumenter per topic
        factory.setConcurrency(3);
        
        // Error handling: Hvordan vi håndterer feil i meldingsprosessering
        factory.setCommonErrorHandler(null); // Kan legge til custom error handler
        
        // Acknowledgment mode: Manual for bedre kontroll
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        return factory;
    }

    /**
     * Admin konfigurasjon for å administrere topics
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    // Topics - definerer hvilke kanaler vi bruker for kommunikasjon

    /**
     * Topic for sak-hendelser
     * Alle hendelser relatert til saksbehandling sendes hit
     */
    @Bean
    public NewTopic sakHendelserTopic() {
        return TopicBuilder.name("nav.sak.hendelser")
                .partitions(3)  // Parallellisering
                .replicas(1)    // Redundans (sett høyere i produksjon)
                .compact()      // Behold siste melding per nøkkel
                .config("retention.ms", "604800000") // 1 uke
                .config("compression.type", "snappy") // Komprimering
                .build();
    }

    /**
     * Topic for bruker-hendelser
     * Alle hendelser relatert til brukerdata
     */
    @Bean
    public NewTopic brukerHendelserTopic() {
        return TopicBuilder.name("nav.bruker.hendelser")
                .partitions(2)
                .replicas(1)
                .compact()
                .config("retention.ms", "2592000000") // 30 dager
                .build();
    }

    /**
     * Topic for vedtak-hendelser
     * Kritiske hendelser som trigger utbetalinger
     */
    @Bean
    public NewTopic vedtakHendelserTopic() {
        return TopicBuilder.name("nav.vedtak.hendelser")
                .partitions(5)  // Høy throughput
                .replicas(1)
                .config("retention.ms", "31536000000") // 1 år (kritiske data)
                .config("min.insync.replicas", "1") // Sikkerhet
                .build();
    }

    /**
     * Topic for integrasjon med eksterne systemer
     * Kommunikasjon med folkeregister, banker, etc.
     */
    @Bean
    public NewTopic eksternIntegrasjonTopic() {
        return TopicBuilder.name("nav.ekstern.integrasjon")
                .partitions(2)
                .replicas(1)
                .config("retention.ms", "86400000") // 1 dag
                .build();
    }

    /**
     * Dead Letter Topic for feilede meldinger
     * Viktig for robuste integrasjoner
     */
    @Bean
    public NewTopic deadLetterTopic() {
        return TopicBuilder.name("nav.dead.letter")
                .partitions(1)
                .replicas(1)
                .config("retention.ms", "2592000000") // 30 dager
                .build();
    }
}

/**
 * Kafka topic konstanter
 * Brukes i hele applikasjonen for konsistens
 */
class KafkaTopics {
    public static final String SAK_HENDELSER = "nav.sak.hendelser";
    public static final String BRUKER_HENDELSER = "nav.bruker.hendelser";
    public static final String VEDTAK_HENDELSER = "nav.vedtak.hendelser";
    public static final String EKSTERN_INTEGRASJON = "nav.ekstern.integrasjon";
    public static final String DEAD_LETTER = "nav.dead.letter";
}
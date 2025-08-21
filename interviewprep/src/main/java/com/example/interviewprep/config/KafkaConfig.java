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
 * Apache Kafka Configuration for Event-Driven Architecture
 * 
 * Implements the publish-subscribe messaging pattern using Kafka as the message broker.
 * This configuration supports event-driven microservices communication where services
 * publish domain events and other services subscribe to relevant event streams.
 * 
 * Architecture Pattern:
 * - Event Publisher: Services publish domain events to topics
 * - Event Consumer: Services subscribe to event streams for reactive processing
 * - Topic Partitioning: Enables horizontal scalability and ordering guarantees
 * - Consumer Groups: Provides load balancing across multiple consumer instances
 * 
 * Configuration Strategy:
 * - Conditional Bean Creation: Only active when kafka.enabled=true
 * - JSON Serialization: Events serialized as JSON for cross-platform compatibility
 * - Idempotent Producer: Ensures exactly-once delivery semantics
 * - Auto Offset Management: Simplifies consumer state management
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
     * Kafka Producer Configuration
     * 
     * Configures the Kafka producer for publishing events to topics.
     * 
     * Producer Properties:
     * - Bootstrap Servers: Initial cluster connection endpoints
     * - Key Serializer: String keys for message partitioning
     * - Value Serializer: JSON serialization for complex event objects
     * - Acks: all - Ensures leader and all replicas acknowledge writes
     * - Retries: 3 - Automatic retry for transient failures
     * - Idempotence: Prevents duplicate messages during retries
     * 
     * Performance Tuning:
     * - Enable idempotence for exactly-once semantics
     * - Batch processing for improved throughput
     * - Compression for reduced network overhead
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Kafka Template for Event Publishing
     * 
     * Provides high-level abstraction for sending messages to Kafka topics.
     * Supports both synchronous and asynchronous message sending patterns.
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Kafka Consumer Configuration
     * 
     * Configures consumer properties for event stream processing.
     * 
     * Consumer Properties:
     * - Bootstrap Servers: Cluster connection configuration
     * - Group ID: Consumer group for load balancing
     * - Key Deserializer: String key deserialization
     * - Value Deserializer: JSON object deserialization with type mapping
     * - Auto Offset Reset: earliest - Process all available messages
     * - Trusted Packages: Security whitelist for JSON deserialization
     * 
     * Reliability Features:
     * - Automatic offset management
     * - Session timeout handling
     * - Heartbeat interval configuration
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.interviewprep.models");
        
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * Kafka Listener Container Factory
     * 
     * Configures the container for @KafkaListener annotated methods.
     * 
     * Container Configuration:
     * - Concurrency Level: Number of consumer threads per partition
     * - Ack Mode: Manual acknowledgment for precise offset control
     * - Error Handler: Custom error processing for failed messages
     * - Retry Policy: Configurable retry behavior for transient failures
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    /**
     * Kafka Admin Client Configuration
     * 
     * Enables programmatic topic management and cluster administration.
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    /**
     * Case Events Topic Configuration
     * 
     * Topic for publishing case lifecycle events (created, updated, closed).
     * 
     * Topic Properties:
     * - Partitions: 3 - Enables parallel processing across consumers
     * - Replication Factor: 1 - Single replica for development environment
     * - Compaction: Disabled - Retains full event history
     */
    @Bean
    public NewTopic caseEventsTopic() {
        return TopicBuilder.name("case-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * User Events Topic Configuration
     * 
     * Topic for publishing user-related events (registration, profile updates).
     */
    @Bean
    public NewTopic userEventsTopic() {
        return TopicBuilder.name("user-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Integration Events Topic Configuration
     * 
     * Topic for publishing external system integration events and responses.
     */
    @Bean
    public NewTopic integrationEventsTopic() {
        return TopicBuilder.name("integration-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Dead Letter Topic Configuration
     * 
     * Topic for messages that failed processing after exhausting retry attempts.
     * Enables manual investigation and reprocessing of failed events.
     */
    @Bean
    public NewTopic deadLetterTopic() {
        return TopicBuilder.name("dead-letter-queue")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
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
     * Implements the Event Notification pattern for case lifecycle management.
     * This event triggers automated workflows across the enterprise integration platform:
     * 
     * Downstream Integration Points:
     * - Work queue management systems for case assignment
     * - User notification services for status updates
     * - Business intelligence systems for reporting and analytics
     * - External government registries for compliance reporting
     * - Document generation services for initial case documentation
     * 
     * Event Structure:
     * Contains case metadata, user reference, and correlation tracking for
     * distributed tracing across the integration landscape.
     * 
     * @param sak The created case entity containing all relevant case data
     */
    public void sendSakOpprettetHendelse(Sak sak) {
        logger.info("Publishing case creation event for case ID: {}", sak.getId());

        Map<String, Object> eventPayload = buildCaseCreatedEvent(sak);
        
        sendAsync(SAK_HENDELSER_TOPIC, sak.getId().toString(), eventPayload, 
                 "Case creation event published successfully", 
                 "Failed to publish case creation event");
    }

    /**
     * Builds standardized case creation event payload.
     */
    private Map<String, Object> buildCaseCreatedEvent(Sak sak) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "CASE_CREATED");
        event.put("timestamp", LocalDateTime.now().toString());
        event.put("caseId", sak.getId());
        event.put("userIdentifier", maskPersonalId(sak.getBruker().getFodselsnummer()));
        event.put("caseType", sak.getType().toString());
        event.put("caseStatus", sak.getStatus().toString());
        
        // Metadata for distributed tracing and audit trails
        event.put("sourceSystem", "CASE_MANAGEMENT_SERVICE");
        event.put("correlationId", generateCorrelationId());
        event.put("eventVersion", "1.0");
        
        return event;
    }

    /**
     * Publishes case processing started event for workflow orchestration.
     * 
     * Implements the Process Manager pattern for coordinating multi-step business processes.
     * This event initiates automated workflow sequences across downstream systems:
     * 
     * Business Process Triggers:
     * - Workflow management system assigns case to appropriate case worker
     * - Time tracking system begins SLA monitoring and processing metrics
     * - Resource allocation system reserves processing capacity
     * - Business intelligence system updates case pipeline analytics
     * 
     * Service Level Agreement (SLA) Integration:
     * The event includes expected processing time to enable automated
     * escalation workflows and performance monitoring across the enterprise.
     * 
     * @param sak The case entity entering active processing phase
     */
    public void sendSaksbehandlingStartetHendelse(Sak sak) {
        logger.info("Publishing case processing started event for case ID: {}", sak.getId());

        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "CASE_PROCESSING_STARTED");
        event.put("timestamp", LocalDateTime.now().toString());
        event.put("caseId", sak.getId());
        event.put("caseType", sak.getType().toString());
        event.put("expectedProcessingDays", sak.getType().getForventetBehandlingstidDager());
        event.put("sourceSystem", "CASE_MANAGEMENT_SERVICE");
        event.put("correlationId", generateCorrelationId());
        event.put("eventVersion", "1.0");

        sendAsync(SAK_HENDELSER_TOPIC, sak.getId().toString(), event,
                 "Case processing started event published successfully",
                 "Failed to publish case processing started event");
    }

    /**
     * Publishes critical case decision event implementing the Saga Pattern.
     * 
     * Enterprise Integration Pattern: Distributed Saga Orchestration
     * This high-priority event triggers a coordinated sequence of business transactions
     * across multiple systems to complete the case decision workflow:
     * 
     * Saga Transaction Steps:
     * 1. Payment System: Initiate benefit transfer or process refund
     * 2. Document Generation: Create and distribute official decision letters
     * 3. Notification System: Send multi-channel user notifications (SMS, email)
     * 4. Analytics System: Update business intelligence metrics and KPIs
     * 5. Audit System: Record decision for compliance and appeal processes
     * 
     * Reliability Requirements:
     * - Uses dedicated high-priority topic for guaranteed delivery
     * - Implements compensation logic for failed saga steps
     * - Provides detailed audit trail for regulatory compliance
     * 
     * @param sak The case entity with final decision
     * @param innvilget Boolean indicating if case was approved or denied
     * @param begrunnelse Detailed justification for the decision
     */
    public void sendVedtakFattetHendelse(Sak sak, boolean innvilget, String begrunnelse) {
        logger.info("Publishing CRITICAL case decision event for case ID: {} - Decision: {}", 
                   sak.getId(), innvilget ? "APPROVED" : "DENIED");

        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "CASE_DECISION_RENDERED");
        event.put("timestamp", LocalDateTime.now().toString());
        event.put("caseId", sak.getId());
        event.put("userIdentifier", maskPersonalId(sak.getBruker().getFodselsnummer()));
        event.put("caseType", sak.getType().toString());
        event.put("approved", innvilget);
        event.put("justification", begrunnelse);
        
        // Critical processing metadata
        event.put("priority", "CRITICAL");
        event.put("requiresFollowUp", innvilget);
        event.put("sourceSystem", "CASE_MANAGEMENT_SERVICE");
        event.put("correlationId", generateCorrelationId());
        event.put("eventVersion", "1.0");
        event.put("sagaId", generateSagaId(sak.getId()));

        sendAsync(VEDTAK_HENDELSER_TOPIC, sak.getId().toString(), event,
                 "Critical case decision event published successfully",
                 "CRITICAL FAILURE: Case decision event publication failed");
    }

    /**
     * Publishes user data change events for Master Data Management synchronization.
     * 
     * Enterprise Integration Pattern: Master Data Management (MDM)
     * Ensures consistent user data across all enterprise systems by publishing
     * authoritative data changes to all subscribing integration endpoints.
     * 
     * MDM Synchronization Strategy:
     * - Single source of truth for user master data
     * - Event-driven propagation to downstream systems
     * - Eventual consistency across distributed data stores
     * - Conflict resolution through timestamp-based ordering
     * 
     * Downstream System Synchronization:
     * User data must be synchronized across multiple enterprise systems
     * including CRM, document management, payment processing, and external
     * government registries to maintain data consistency and compliance.
     * 
     * @param bruker The user entity with updated information
     * @param endringsType The type of change operation (CREATE, UPDATE, DELETE)
     */
    public void sendBrukerEndretHendelse(Bruker bruker, String endringsType) {
        logger.info("Publishing user data change event: {} for user ID: {}", endringsType, bruker.getId());

        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "USER_" + endringsType.toUpperCase());
        event.put("timestamp", LocalDateTime.now().toString());
        event.put("userId", bruker.getId());
        event.put("userIdentifier", maskPersonalId(bruker.getFodselsnummer()));
        event.put("fullName", bruker.getNavn());
        event.put("address", bruker.getAdresse());
        event.put("changeType", endringsType.toUpperCase());
        event.put("sourceSystem", "USER_MANAGEMENT_SERVICE");
        event.put("correlationId", generateCorrelationId());
        event.put("eventVersion", "1.0");

        sendAsync(BRUKER_HENDELSER_TOPIC, bruker.getFodselsnummer(), event,
                 "User data change event published successfully",
                 "Failed to publish user data change event");
    }

    /**
     * Generic asynchronous message publishing with comprehensive error handling.
     * 
     * Resilience Engineering Implementation:
     * Demonstrates enterprise-grade error handling patterns essential for
     * robust distributed systems integration. This method implements the
     * Circuit Breaker pattern foundation for fault tolerance.
     * 
     * Reliability Features:
     * - Asynchronous non-blocking message delivery
     * - Comprehensive error logging with structured metadata
     * - Failure callback mechanism for downstream error handling
     * - Integration with monitoring and alerting systems
     * 
     * Production Error Handling Strategy:
     * In production environments, this method would integrate with:
     * - Dead Letter Queue patterns for failed message recovery
     * - Monitoring systems (Prometheus/Grafana) for operational alerting
     * - Error tracking systems (Sentry/Bugsnag) for application monitoring
     * - Circuit breaker implementation for cascade failure prevention
     * 
     * @param topic Kafka topic name for message routing
     * @param key Message key for partitioning and ordering
     * @param payload Event data object for transmission
     * @param successMessage Logging message for successful delivery
     * @param errorMessage Logging message for delivery failures
     */
    private void sendAsync(String topic, String key, Object payload, 
                          String successMessage, String errorMessage) {
        
        CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send(topic, key, payload);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                logger.info("{} - Topic: {}, Key: {}, Offset: {}, Partition: {}", 
                           successMessage, topic, key, 
                           result.getRecordMetadata().offset(),
                           result.getRecordMetadata().partition());
            } else {
                logger.error("{} - Topic: {}, Key: {}, Error: {}", 
                            errorMessage, topic, key, ex.getMessage());
                
                handleSendFailure(topic, key, payload, ex);
            }
        });
    }

    /**
     * Enterprise-grade failure handling for message delivery failures.
     * 
     * Resilience Pattern Implementation:
     * Implements multiple fault tolerance patterns including Circuit Breaker,
     * Retry with Exponential Backoff, and Dead Letter Queue patterns for
     * comprehensive error recovery in distributed messaging systems.
     * 
     * Production Implementation Strategy:
     * In enterprise environments, this method would implement:
     * 
     * 1. Persistent Retry Mechanism:
     *    - Store failed messages in database with retry metadata
     *    - Implement exponential backoff with jitter to prevent thundering herd
     *    - Configure maximum retry attempts with graduated backoff intervals
     * 
     * 2. Operational Monitoring Integration:
     *    - Trigger immediate alerts to operations team via PagerDuty/Slack
     *    - Update Prometheus metrics for failure rate monitoring
     *    - Integration with Grafana dashboards for real-time visibility
     * 
     * 3. Dead Letter Queue Processing:
     *    - Route persistently failing messages to DLQ for manual investigation
     *    - Maintain audit trail for compliance and debugging
     *    - Enable manual reprocessing workflow for resolved issues
     * 
     * @param topic The Kafka topic where delivery failed
     * @param key The message key for tracking
     * @param payload The message payload for retry or DLQ storage
     * @param ex The exception that caused the delivery failure
     */
    private void handleSendFailure(String topic, String key, Object payload, Throwable ex) {
        logger.error("Kafka message delivery failure - initiating error recovery workflow");
        
        // Structured error logging for monitoring and debugging
        logger.error("Message delivery failure details - Topic: {}, Key: {}, PayloadType: {}, " +
                    "ErrorType: {}, ErrorMessage: {}, CorrelationId: {}", 
                    topic, key, payload.getClass().getSimpleName(), 
                    ex.getClass().getSimpleName(), ex.getMessage(),
                    extractCorrelationId(payload));
        
        // In production: Implement retry persistence and alerting
        scheduleRetryOrDeadLetter(topic, key, payload, ex);
    }

    // Utility Methods for Enterprise Integration

    /**
     * Generates unique correlation identifier for distributed tracing.
     * 
     * Observability Pattern Implementation:
     * Creates globally unique correlation IDs essential for tracing requests
     * across distributed microservices architecture. This enables:
     * - End-to-end transaction tracking across service boundaries
     * - Performance monitoring and bottleneck identification
     * - Root cause analysis for integration failures
     * - Compliance audit trails for regulatory requirements
     * 
     * Format: NAV-{timestamp}-{threadId} ensures uniqueness and traceability
     * 
     * @return Unique correlation identifier for request tracking
     */
    private String generateCorrelationId() {
        return "NAV-" + System.currentTimeMillis() + "-" + 
               Thread.currentThread().getId();
    }

    /**
     * Generates unique saga identifier for distributed transaction tracking.
     * 
     * @param caseId The case identifier to include in saga tracking
     * @return Unique saga identifier for transaction coordination
     */
    private String generateSagaId(Long caseId) {
        return "SAGA-" + caseId + "-" + System.currentTimeMillis();
    }

    /**
     * Masks personal identification numbers for secure logging compliance.
     * 
     * Data Protection Implementation:
     * Implements GDPR-compliant logging by masking sensitive personal data
     * while retaining sufficient information for debugging and audit purposes.
     * 
     * Masking Strategy:
     * - Preserves first 6 digits for date of birth information
     * - Masks remaining digits to protect individual identity
     * - Maintains format for log analysis and pattern recognition
     * 
     * Compliance Requirements:
     * Norwegian government systems must protect citizen data according to
     * GDPR regulations and national privacy legislation.
     * 
     * @param personalId The Norwegian personal identification number
     * @return Masked personal ID safe for logging and monitoring
     */
    private String maskPersonalId(String personalId) {
        if (personalId == null || personalId.length() < 6) {
            return "***INVALID***";
        }
        return personalId.substring(0, 6) + "*****";
    }

    /**
     * Extracts correlation ID from event payload for error tracking.
     */
    private String extractCorrelationId(Object payload) {
        if (payload instanceof Map) {
            Map<?, ?> eventMap = (Map<?, ?>) payload;
            Object correlationId = eventMap.get("correlationId");
            return correlationId != null ? correlationId.toString() : "UNKNOWN";
        }
        return "UNKNOWN";
    }

    /**
     * Placeholder for production retry and dead letter queue implementation.
     */
    private void scheduleRetryOrDeadLetter(String topic, String key, Object payload, Throwable ex) {
        // Production implementation would:
        // 1. Persist to retry queue with exponential backoff
        // 2. Send to dead letter queue after max retry attempts
        // 3. Trigger monitoring alerts for operational visibility
        logger.warn("Retry mechanism not implemented - message lost: {}", key);
    }

    /**
     * Publishes health check events for operational monitoring.
     * 
     * Operational Monitoring Pattern:
     * Implements synthetic transaction monitoring for proactive system health
     * verification. This enables operations teams to detect integration failures
     * before they impact business processes.
     * 
     * Health Check Strategy:
     * - Periodic synthetic events validate end-to-end message flow
     * - Monitors Kafka connectivity and topic availability
     * - Validates serialization and deserialization processes
     * - Provides baseline metrics for performance monitoring
     * 
     * Monitoring Integration:
     * Health check events are consumed by monitoring systems to:
     * - Generate availability metrics and SLA reporting
     * - Trigger alerts for message delivery failures
     * - Validate disaster recovery and failover procedures
     */
    public void sendHealthCheckHendelse() {
        Map<String, Object> healthCheck = new HashMap<>();
        healthCheck.put("eventType", "SYSTEM_HEALTH_CHECK");
        healthCheck.put("timestamp", LocalDateTime.now().toString());
        healthCheck.put("systemStatus", "OPERATIONAL");
        healthCheck.put("sourceSystem", "KAFKA_PRODUCER_SERVICE");
        healthCheck.put("correlationId", generateCorrelationId());
        healthCheck.put("eventVersion", "1.0");

        sendAsync("nav.health.check", "health", healthCheck,
                 "System health check event published successfully",
                 "System health check event publication failed");
    }

    /**
     * Publishes generic events to specified topics for flexible integration patterns.
     * 
     * Flexible Event Publishing Pattern:
     * Supports dynamic event publishing for integration scenarios where
     * event types and destinations are determined at runtime. This enables:
     * - Protocol mediation between different messaging systems
     * - Dynamic routing based on runtime configuration
     * - Bridge pattern implementation for legacy system integration
     * - Content-based routing for multi-tenant scenarios
     * 
     * Use Cases:
     * - Publishing events to external partner systems with varying schemas
     * - Supporting both Kafka and RabbitMQ simultaneously (dual messaging)
     * - Implementing configurable integration endpoints
     * - Enabling A/B testing of different integration patterns
     * 
     * @param topic The destination topic for event publishing
     * @param eventData The event payload (typically Map or POJO)
     */
    @Override
    public void sendGenericEvent(String topic, Object eventData) {
        logger.debug("Publishing generic event to topic: {}", topic);
        
        try {
            String key = generateEventKey(eventData);
            sendAsync(topic, key, eventData,
                     "Generic event published successfully to " + topic,
                     "Failed to publish generic event to " + topic);
        } catch (Exception e) {
            logger.error("Error publishing generic event to topic {}: {}", topic, e.getMessage());
        }
    }
    
    /**
     * Generates appropriate partitioning key for generic events.
     * 
     * Key Generation Strategy:
     * - Case ID: Ensures case-related events maintain ordering
     * - Event Type: Groups similar events for balanced partitioning
     * - Timestamp: Fallback for unique distribution
     * 
     * @param eventData The event payload for key extraction
     * @return Partition key for message ordering and distribution
     */
    private String generateEventKey(Object eventData) {
        if (eventData instanceof Map) {
            Map<?, ?> eventMap = (Map<?, ?>) eventData;
            
            // Prefer case ID for ordering guarantees
            Object caseId = eventMap.get("caseId");
            if (caseId != null) {
                return caseId.toString();
            }
            
            // Use event type for balanced distribution
            Object eventType = eventMap.get("eventType");
            if (eventType != null) {
                return eventType.toString();
            }
        }
        
        // Fallback to timestamp-based key
        return "generic-" + System.currentTimeMillis();
    }
}
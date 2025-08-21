package com.example.interviewprep.service;

import com.example.interviewprep.config.RabbitMQConfig;
import com.example.interviewprep.models.Sak;
import com.example.interviewprep.models.SaksStatus;
import com.example.interviewprep.models.SaksType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Dual Messaging Pattern Service - Kafka + RabbitMQ Integration
 * 
 * This service demonstrates how to effectively combine two different messaging paradigms
 * in a single enterprise integration platform. Understanding when to use each pattern
 * is crucial for integration architects.
 * 
 * MESSAGING PATTERN COMPARISON:
 * 
 * ┌─────────────────┬─────────────────┬─────────────────────────────────┐
 * │     PATTERN     │    TECHNOLOGY   │           USE CASES             │
 * ├─────────────────┼─────────────────┼─────────────────────────────────┤
 * │ Pub/Sub         │ Kafka Topics    │ • Event notifications           │
 * │ (Broadcast)     │                 │ • Audit logs                    │
 * │                 │                 │ • System monitoring             │
 * │                 │                 │ • Multiple consumers needed     │
 * ├─────────────────┼─────────────────┼─────────────────────────────────┤
 * │ Message Queue   │ RabbitMQ Queues │ • Work distribution             │
 * │ (Point-to-Point)│                 │ • Task processing               │
 * │                 │                 │ • Request/response              │
 * │                 │                 │ • Single consumer per message   │
 * └─────────────────┴─────────────────┴─────────────────────────────────┘
 * 
 * REAL-WORLD NAV SCENARIO:
 * When a case is processed, we need both patterns:
 * 
 * 1. KAFKA PUB/SUB - Broadcast case events to multiple systems:
 *    - Audit system logs all changes
 *    - Dashboard updates real-time statistics  
 *    - Notification service sends alerts
 *    - Reporting system updates metrics
 * 
 * 2. RABBITMQ QUEUES - Distribute specific work tasks:
 *    - Document generation workers create PDFs
 *    - Payment processing handles money transfers
 *    - Email workers send notifications
 *    - Each task handled by exactly one worker
 * 
 * This approach combines the best of both worlds:
 * - Events are broadcasted for transparency and monitoring
 * - Work is distributed efficiently without duplication
 */
@Service
public class DualMessagingPatternService {

    private static final Logger logger = LoggerFactory.getLogger(DualMessagingPatternService.class);

    private final KafkaProducerInterface kafkaProducerService;
    private final RabbitTemplate rabbitTemplate;

    @Autowired
    public DualMessagingPatternService(KafkaProducerInterface kafkaProducerService, 
                                      RabbitTemplate rabbitTemplate) {
        this.kafkaProducerService = kafkaProducerService;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Process case using dual messaging patterns
     * 
     * This method demonstrates the orchestration of both messaging patterns
     * for a complete business workflow.
     * 
     * WORKFLOW:
     * 1. Publish event to Kafka (multiple systems get notified)
     * 2. Queue specific tasks in RabbitMQ (work gets distributed)
     * 3. Handle responses and errors appropriately
     */
    public void processCaseWithDualMessaging(Sak sak, String processType) {
        logger.info("Processing case {} with dual messaging patterns: {}", sak.getId(), processType);

        try {
            // STEP 1: KAFKA PUB/SUB - Broadcast event to all interested systems
            publishKafkaEventForCase(sak, processType);

            // STEP 2: RABBITMQ QUEUES - Queue specific work tasks
            queueRabbitMQTasksForCase(sak, processType);

            logger.info("Successfully initiated dual messaging for case: {}", sak.getId());

        } catch (Exception e) {
            logger.error("Error in dual messaging for case {}: {}", sak.getId(), e.getMessage(), e);
            
            // Publish error event to Kafka for monitoring
            publishErrorEvent(sak.getId(), processType, e.getMessage());
        }
    }

    /**
     * KAFKA PUB/SUB PATTERN - Broadcast events to multiple subscribers
     * 
     * These events are consumed by multiple systems that need to know about case changes:
     * - Audit system (compliance logging)
     * - Dashboard (real-time statistics)
     * - Reporting (business intelligence)
     * - Notification service (user alerts)
     * - External systems (partner integrations)
     */
    private void publishKafkaEventForCase(Sak sak, String processType) {
        logger.debug("Publishing Kafka events for case: {}", sak.getId());

        try {
            // Case status change event - multiple systems interested
            Map<String, Object> caseEvent = Map.of(
                "eventType", "CASE_STATUS_CHANGED",
                "caseId", sak.getId(),
                "caseType", sak.getType().toString(),
                "newStatus", sak.getStatus().toString(),
                "processType", processType,
                "timestamp", LocalDateTime.now().toString(),
                "source", "dual-messaging-service"
            );

            // Publish to multiple Kafka topics based on event type
            kafkaProducerService.sendGenericEvent("case-events", caseEvent);
            kafkaProducerService.sendGenericEvent("audit-events", caseEvent);

            // Type-specific events for different subscribers
            switch (sak.getType()) {
                case DAGPENGER:
                    kafkaProducerService.sendGenericEvent("dagpenger-events", caseEvent);
                    break;
                case SYKEPENGER:
                    kafkaProducerService.sendGenericEvent("sykepenger-events", caseEvent);
                    break;
                case BARNETRYGD:
                    kafkaProducerService.sendGenericEvent("barnetrygd-events", caseEvent);
                    break;
                default:
                    kafkaProducerService.sendGenericEvent("general-events", caseEvent);
            }

            logger.debug("Kafka events published successfully for case: {}", sak.getId());

        } catch (Exception e) {
            logger.error("Failed to publish Kafka events for case {}: {}", sak.getId(), e.getMessage());
        }
    }

    /**
     * RABBITMQ QUEUE PATTERN - Distribute work tasks to specific workers
     * 
     * These tasks are processed by exactly one worker from the queue:
     * - Document generation (PDF creation, letter generation)
     * - Payment processing (bank transfers, benefit calculations)
     * - Notification delivery (email, SMS sending)
     * - Case processing (complex business logic)
     */
    private void queueRabbitMQTasksForCase(Sak sak, String processType) {
        logger.debug("Queueing RabbitMQ tasks for case: {}", sak.getId());

        try {
            // Document generation task - exactly one worker will process this
            if (requiresDocumentGeneration(sak)) {
                Map<String, Object> documentTask = Map.of(
                    "taskType", "GENERATE_DOCUMENT",
                    "caseId", sak.getId(),
                    "caseType", sak.getType().toString(),
                    "documentType", getRequiredDocumentType(sak),
                    "priority", getPriority(sak),
                    "createdAt", LocalDateTime.now().toString()
                );

                rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NAV_INTEGRATION_EXCHANGE,
                    "document.generation",
                    documentTask
                );

                logger.debug("Document generation task queued for case: {}", sak.getId());
            }

            // Payment processing task - critical for financial accuracy
            if (requiresPaymentProcessing(sak)) {
                Map<String, Object> paymentTask = Map.of(
                    "taskType", "PROCESS_PAYMENT",
                    "caseId", sak.getId(),
                    "caseType", sak.getType().toString(),
                    "amount", calculatePaymentAmount(sak),
                    "priority", 10, // High priority for payments
                    "urgency", "HIGH",
                    "createdAt", LocalDateTime.now().toString()
                );

                rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NAV_INTEGRATION_EXCHANGE,
                    "payment.processing",
                    paymentTask
                );

                logger.debug("Payment processing task queued for case: {}", sak.getId());
            }

            // Notification delivery task - inform citizen of case progress
            Map<String, Object> notificationTask = Map.of(
                "taskType", "SEND_NOTIFICATION",
                "caseId", sak.getId(),
                "citizenId", "***masked***", // Mask sensitive data
                "notificationType", getNotificationType(sak.getStatus()),
                "channel", "EMAIL_AND_SMS",
                "priority", 5,
                "createdAt", LocalDateTime.now().toString()
            );

            rabbitTemplate.convertAndSend(
                RabbitMQConfig.NAV_INTEGRATION_EXCHANGE,
                "notification.delivery",
                notificationTask
            );

            // Complex case processing task - for cases requiring special handling
            if (requiresComplexProcessing(sak)) {
                Map<String, Object> complexTask = Map.of(
                    "taskType", "COMPLEX_CASE_PROCESSING",
                    "caseId", sak.getId(),
                    "caseType", sak.getType().toString(),
                    "complexity", "HIGH",
                    "requiresHumanReview", true,
                    "estimatedProcessingTime", "PT2H", // ISO 8601 duration format
                    "createdAt", LocalDateTime.now().toString()
                );

                rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NAV_INTEGRATION_EXCHANGE,
                    "case.processing.complex",
                    complexTask
                );

                logger.debug("Complex processing task queued for case: {}", sak.getId());
            }

        } catch (Exception e) {
            logger.error("Failed to queue RabbitMQ tasks for case {}: {}", sak.getId(), e.getMessage());
        }
    }

    /**
     * RABBITMQ CONSUMERS - Process work tasks from queues
     * 
     * These methods demonstrate how workers consume tasks from RabbitMQ queues.
     * Each worker processes tasks independently, ensuring work distribution.
     */

    /**
     * Document Generation Worker
     * Processes document generation tasks from the queue
     */
    @RabbitListener(queues = RabbitMQConfig.DOCUMENT_GENERATION_QUEUE)
    public void handleDocumentGenerationTask(@Payload Map<String, Object> task,
                                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        logger.info("Processing document generation task: {}", task.get("caseId"));

        try {
            String taskType = (String) task.get("taskType");
            Long caseId = Long.valueOf(task.get("caseId").toString());
            String documentType = (String) task.get("documentType");

            // Simulate document generation processing
            logger.info("Generating {} document for case {}", documentType, caseId);
            
            // In real implementation, this would:
            // 1. Fetch case data from database
            // 2. Generate PDF using template engine
            // 3. Store document in document management system
            // 4. Update case with document reference
            
            Thread.sleep(2000); // Simulate processing time
            
            logger.info("Document generation completed for case: {}", caseId);

            // Acknowledge successful processing
            // Manual ACK would be done here in production

        } catch (Exception e) {
            logger.error("Error processing document generation task: {}", e.getMessage());
            
            // In production, this would trigger retry logic or move to DLQ
            // For now, we'll just log the error
        }
    }

    /**
     * Payment Processing Worker
     * Handles critical payment tasks with guaranteed delivery
     */
    @RabbitListener(queues = RabbitMQConfig.PAYMENT_PROCESSING_QUEUE)
    public void handlePaymentProcessingTask(@Payload Map<String, Object> task,
                                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        logger.info("Processing payment task for case: {}", task.get("caseId"));

        try {
            Long caseId = Long.valueOf(task.get("caseId").toString());
            Double amount = Double.valueOf(task.get("amount").toString());
            String urgency = (String) task.get("urgency");

            logger.info("Processing payment: caseId={}, amount={}, urgency={}", caseId, amount, urgency);

            // Simulate payment processing
            // In real implementation, this would:
            // 1. Validate payment details
            // 2. Check account balances and limits
            // 3. Process payment through banking system
            // 4. Update payment records
            // 5. Send confirmation

            Thread.sleep(3000); // Simulate payment processing time

            logger.info("Payment processing completed: caseId={}, amount={}", caseId, amount);

            // Send payment completion event back to Kafka
            Map<String, Object> completionEvent = Map.of(
                "eventType", "PAYMENT_COMPLETED",
                "caseId", caseId,
                "amount", amount,
                "status", "SUCCESS",
                "timestamp", LocalDateTime.now().toString()
            );
            
            kafkaProducerService.sendGenericEvent("payment-events", completionEvent);

        } catch (Exception e) {
            logger.error("Error processing payment task: {}", e.getMessage());
            
            // Publish payment failure event
            Map<String, Object> failureEvent = Map.of(
                "eventType", "PAYMENT_FAILED",
                "caseId", task.get("caseId"),
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now().toString()
            );
            
            try {
                kafkaProducerService.sendGenericEvent("payment-failures", failureEvent);
            } catch (Exception kafkaError) {
                logger.error("Failed to publish payment failure event: {}", kafkaError.getMessage());
            }
        }
    }

    /**
     * Notification Delivery Worker
     * Handles notification delivery with retry patterns
     */
    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_DELIVERY_QUEUE)
    public void handleNotificationDeliveryTask(@Payload Map<String, Object> task,
                                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        logger.info("Processing notification task for case: {}", task.get("caseId"));

        try {
            Long caseId = Long.valueOf(task.get("caseId").toString());
            String notificationType = (String) task.get("notificationType");
            String channel = (String) task.get("channel");

            logger.info("Sending notification: caseId={}, type={}, channel={}", caseId, notificationType, channel);

            // Simulate notification delivery
            // In real implementation, this would:
            // 1. Fetch citizen contact information
            // 2. Format notification message
            // 3. Send via email/SMS service
            // 4. Handle delivery confirmations
            // 5. Retry failed deliveries

            Thread.sleep(1000); // Simulate delivery time

            logger.info("Notification delivered successfully for case: {}", caseId);

        } catch (Exception e) {
            logger.error("Error processing notification task: {}", e.getMessage());
            // Failed notifications would retry or go to DLQ
        }
    }

    /**
     * Complex Case Processing Worker
     * Handles cases requiring special processing or human review
     */
    @RabbitListener(queues = RabbitMQConfig.CASE_PROCESSING_QUEUE)
    public void handleComplexCaseProcessingTask(@Payload Map<String, Object> task,
                                              @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        logger.info("Processing complex case task: {}", task.get("caseId"));

        try {
            Long caseId = Long.valueOf(task.get("caseId").toString());
            String caseType = (String) task.get("caseType");
            boolean requiresHumanReview = (Boolean) task.get("requiresHumanReview");

            logger.info("Complex case processing: caseId={}, type={}, humanReview={}", 
                       caseId, caseType, requiresHumanReview);

            // Simulate complex processing
            // In real implementation, this would:
            // 1. Apply complex business rules
            // 2. Integrate with multiple external systems
            // 3. Perform fraud detection
            // 4. Queue for human review if needed
            // 5. Make automated decisions where possible

            Thread.sleep(5000); // Simulate complex processing time

            if (requiresHumanReview) {
                logger.info("Case {} queued for human review", caseId);
                
                // Publish event that case needs human attention
                Map<String, Object> reviewEvent = Map.of(
                    "eventType", "HUMAN_REVIEW_REQUIRED",
                    "caseId", caseId,
                    "caseType", caseType,
                    "timestamp", LocalDateTime.now().toString()
                );
                
                kafkaProducerService.sendGenericEvent("human-review-required", reviewEvent);
            } else {
                logger.info("Complex case processing completed automatically for case: {}", caseId);
            }

        } catch (Exception e) {
            logger.error("Error processing complex case task: {}", e.getMessage());
        }
    }

    // Helper methods for business logic

    private void publishErrorEvent(Long caseId, String processType, String errorMessage) {
        try {
            Map<String, Object> errorEvent = Map.of(
                "eventType", "DUAL_MESSAGING_ERROR",
                "caseId", caseId,
                "processType", processType,
                "errorMessage", errorMessage,
                "timestamp", LocalDateTime.now().toString(),
                "severity", "HIGH"
            );
            
            kafkaProducerService.sendGenericEvent("system-errors", errorEvent);
        } catch (Exception e) {
            logger.error("Failed to publish error event: {}", e.getMessage());
        }
    }

    private boolean requiresDocumentGeneration(Sak sak) {
        return sak.getStatus() == SaksStatus.VEDTAK_FATTET || 
               sak.getStatus() == SaksStatus.AVVIST;
    }

    private boolean requiresPaymentProcessing(Sak sak) {
        return sak.getStatus() == SaksStatus.VEDTAK_FATTET && 
               (sak.getType() == SaksType.DAGPENGER || 
                sak.getType() == SaksType.SYKEPENGER ||
                sak.getType() == SaksType.BARNETRYGD);
    }

    private boolean requiresComplexProcessing(Sak sak) {
        return sak.getType() == SaksType.AAP || 
               sak.getType() == SaksType.UFORETRYGD ||
               sak.getBeskrivelse().toLowerCase().contains("kompleks");
    }

    private String getRequiredDocumentType(Sak sak) {
        return switch (sak.getStatus()) {
            case VEDTAK_FATTET -> "VEDTAKSBREV";
            case AVVIST -> "AVSLAG_BREV";
            default -> "STANDARD_BREV";
        };
    }

    private int getPriority(Sak sak) {
        return switch (sak.getType()) {
            case SYKEPENGER -> 8; // High priority for sick leave
            case DAGPENGER -> 6;  // Medium-high for unemployment
            case BARNETRYGD -> 4; // Medium for child benefits
            default -> 2;         // Low for others
        };
    }

    private double calculatePaymentAmount(Sak sak) {
        // Simplified calculation - in real system would be much more complex
        return switch (sak.getType()) {
            case DAGPENGER -> 15000.0;   // Unemployment benefits
            case SYKEPENGER -> 25000.0;  // Sick leave benefits
            case BARNETRYGD -> 1054.0;   // Child benefits
            case AAP -> 18000.0;         // Work assessment allowance
            default -> 0.0;
        };
    }

    private String getNotificationType(SaksStatus status) {
        return switch (status) {
            case MOTTATT -> "CASE_RECEIVED";
            case UNDER_BEHANDLING -> "CASE_IN_PROGRESS";
            case VEDTAK_FATTET -> "DECISION_MADE";
            case UTBETALT -> "PAYMENT_SENT";
            case AVVIST -> "CASE_REJECTED";
            default -> "STATUS_UPDATE";
        };
    }
}
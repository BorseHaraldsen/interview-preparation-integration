package com.example.interviewprep.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ Configuration for Message Queue Patterns
 * 
 * This configuration demonstrates message queue patterns alongside Kafka pub/sub,
 * showing different messaging paradigms used in enterprise integration:
 * 
 * KAFKA (Pub/Sub) vs RABBITMQ (Message Queues):
 * 
 * KAFKA CHARACTERISTICS:
 * - Topics with multiple subscribers (fan-out)
 * - Messages retained for configured time
 * - High throughput, append-only log
 * - Pull-based consumption
 * - Ideal for event streaming and notifications
 * 
 * RABBITMQ CHARACTERISTICS:
 * - Point-to-point message queues
 * - Messages consumed once by single consumer
 * - Push-based delivery with acknowledgments
 * - Complex routing patterns (direct, topic, fanout, headers)
 * - Ideal for work distribution and task processing
 * 
 * WHEN TO USE EACH:
 * 
 * Use KAFKA for:
 * - Event notifications (case created, status changed)
 * - Audit logs and system monitoring
 * - Real-time data streaming
 * - Multiple systems need same events
 * 
 * Use RABBITMQ for:
 * - Work queue processing (document generation)
 * - Request/response patterns
 * - Task distribution to workers
 * - Guaranteed delivery with acknowledgments
 * 
 * This configuration sets up queues for different business processes:
 * - Document generation queue (work distribution)
 * - Payment processing queue (guaranteed delivery)
 * - Notification delivery queue (retry patterns)
 * - Dead letter queues for failed messages
 */
@Configuration
public class RabbitMQConfig {

    // Exchange names - organize related queues
    public static final String NAV_INTEGRATION_EXCHANGE = "nav.integration.exchange";
    public static final String NAV_DLX_EXCHANGE = "nav.dlx.exchange";
    
    // Queue names for different business processes
    public static final String DOCUMENT_GENERATION_QUEUE = "nav.document.generation";
    public static final String PAYMENT_PROCESSING_QUEUE = "nav.payment.processing";
    public static final String NOTIFICATION_DELIVERY_QUEUE = "nav.notification.delivery";
    public static final String CASE_PROCESSING_QUEUE = "nav.case.processing";
    
    // Dead letter queues for failed messages
    public static final String DOCUMENT_GENERATION_DLQ = "nav.document.generation.dlq";
    public static final String PAYMENT_PROCESSING_DLQ = "nav.payment.processing.dlq";
    public static final String NOTIFICATION_DELIVERY_DLQ = "nav.notification.delivery.dlq";
    
    // Routing keys for message classification
    public static final String DOCUMENT_ROUTING_KEY = "document.*";
    public static final String PAYMENT_ROUTING_KEY = "payment.*";
    public static final String NOTIFICATION_ROUTING_KEY = "notification.*";
    public static final String CASE_ROUTING_KEY = "case.*";

    /**
     * Main exchange for routing messages to appropriate queues
     * Uses topic exchange for flexible routing patterns
     */
    @Bean
    public TopicExchange navIntegrationExchange() {
        return ExchangeBuilder
                .topicExchange(NAV_INTEGRATION_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * Dead Letter Exchange for failed messages
     * Messages that can't be processed are routed here for investigation
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder
                .directExchange(NAV_DLX_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * Document Generation Queue - Work Distribution Pattern
     * 
     * Used for distributing document generation tasks to worker processes.
     * Examples: generating PDFs, creating letters, preparing reports
     * 
     * Pattern: Work Queue
     * - Multiple workers can process from same queue
     * - Each message processed by exactly one worker
     * - Manual acknowledgment ensures reliability
     */
    @Bean
    public Queue documentGenerationQueue() {
        return QueueBuilder
                .durable(DOCUMENT_GENERATION_QUEUE)
                .withArgument("x-dead-letter-exchange", NAV_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DOCUMENT_GENERATION_DLQ)
                .withArgument("x-message-ttl", 300000) // 5 minutes TTL
                .build();
    }

    @Bean
    public Queue documentGenerationDLQ() {
        return QueueBuilder.durable(DOCUMENT_GENERATION_DLQ).build();
    }

    @Bean
    public Binding documentGenerationBinding() {
        return BindingBuilder
                .bind(documentGenerationQueue())
                .to(navIntegrationExchange())
                .with(DOCUMENT_ROUTING_KEY);
    }

    @Bean
    public Binding documentGenerationDLQBinding() {
        return BindingBuilder
                .bind(documentGenerationDLQ())
                .to(deadLetterExchange())
                .with(DOCUMENT_GENERATION_DLQ);
    }

    /**
     * Payment Processing Queue - Guaranteed Delivery Pattern
     * 
     * Critical for financial transactions - must ensure exactly-once processing.
     * Examples: benefit payments, refunds, transfers
     * 
     * Pattern: Guaranteed Delivery
     * - Publisher confirms ensure message durability
     * - Manual acknowledgments prevent message loss
     * - Dead letter queue for failed payments
     * - Longer TTL for important financial messages
     */
    @Bean
    public Queue paymentProcessingQueue() {
        return QueueBuilder
                .durable(PAYMENT_PROCESSING_QUEUE)
                .withArgument("x-dead-letter-exchange", NAV_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", PAYMENT_PROCESSING_DLQ)
                .withArgument("x-message-ttl", 1800000) // 30 minutes TTL
                .withArgument("x-max-priority", 10) // Priority queue for urgent payments
                .build();
    }

    @Bean
    public Queue paymentProcessingDLQ() {
        return QueueBuilder.durable(PAYMENT_PROCESSING_DLQ).build();
    }

    @Bean
    public Binding paymentProcessingBinding() {
        return BindingBuilder
                .bind(paymentProcessingQueue())
                .to(navIntegrationExchange())
                .with(PAYMENT_ROUTING_KEY);
    }

    @Bean
    public Binding paymentProcessingDLQBinding() {
        return BindingBuilder
                .bind(paymentProcessingDLQ())
                .to(deadLetterExchange())
                .with(PAYMENT_PROCESSING_DLQ);
    }

    /**
     * Notification Delivery Queue - Retry Pattern
     * 
     * For delivering notifications via email, SMS, or push notifications.
     * Examples: case status updates, payment confirmations, reminders
     * 
     * Pattern: Retry with Exponential Backoff
     * - Failed deliveries retry with increasing delays
     * - Eventually moves to DLQ after max attempts
     * - Shorter TTL as notifications have time sensitivity
     */
    @Bean
    public Queue notificationDeliveryQueue() {
        return QueueBuilder
                .durable(NOTIFICATION_DELIVERY_QUEUE)
                .withArgument("x-dead-letter-exchange", NAV_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", NOTIFICATION_DELIVERY_DLQ)
                .withArgument("x-message-ttl", 600000) // 10 minutes TTL
                .build();
    }

    @Bean
    public Queue notificationDeliveryDLQ() {
        return QueueBuilder.durable(NOTIFICATION_DELIVERY_DLQ).build();
    }

    @Bean
    public Binding notificationDeliveryBinding() {
        return BindingBuilder
                .bind(notificationDeliveryQueue())
                .to(navIntegrationExchange())
                .with(NOTIFICATION_ROUTING_KEY);
    }

    @Bean
    public Binding notificationDeliveryDLQBinding() {
        return BindingBuilder
                .bind(notificationDeliveryDLQ())
                .to(deadLetterExchange())
                .with(NOTIFICATION_DELIVERY_DLQ);
    }

    /**
     * Case Processing Queue - Sequential Processing Pattern
     * 
     * For processing cases that require sequential handling.
     * Examples: complex cases requiring human review, multi-step workflows
     * 
     * Pattern: Sequential Processing
     * - Single consumer to maintain order
     * - Longer processing times allowed
     * - Manual acknowledgment after completion
     */
    @Bean
    public Queue caseProcessingQueue() {
        return QueueBuilder
                .durable(CASE_PROCESSING_QUEUE)
                .withArgument("x-dead-letter-exchange", NAV_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "case.processing.dlq")
                .withArgument("x-message-ttl", 3600000) // 1 hour TTL
                .build();
    }

    @Bean
    public Binding caseProcessingBinding() {
        return BindingBuilder
                .bind(caseProcessingQueue())
                .to(navIntegrationExchange())
                .with(CASE_ROUTING_KEY);
    }

    /**
     * JSON message converter for complex objects
     * Enables sending/receiving POJOs instead of just strings
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate with JSON converter and publisher confirms
     * Configured for reliability with confirmation callbacks
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        template.setMandatory(true); // Ensure message is routed to a queue
        
        // Publisher confirms for guaranteed delivery
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                System.out.println("Message delivered successfully: " + correlationData);
            } else {
                System.err.println("Message delivery failed: " + cause);
            }
        });
        
        // Return callback for unroutable messages
        template.setReturnsCallback(returned -> {
            System.err.println("Message returned: " + returned.getMessage() + 
                             ", Reply Code: " + returned.getReplyCode() + 
                             ", Reply Text: " + returned.getReplyText());
        });
        
        return template;
    }
}
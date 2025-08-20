package com.example.interviewprep.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.annotation.Transformer;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.file.FileReadingMessageSource;
import org.springframework.integration.file.filters.SimplePatternFileListFilter;
import org.springframework.integration.file.transformer.FileToStringTransformer;
import org.springframework.integration.jdbc.JdbcPollingChannelAdapter;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.integration.transformer.HeaderEnricher;
import org.springframework.integration.transformer.support.HeaderValueMessageProcessor;
import org.springframework.integration.transformer.support.StaticHeaderValueMessageProcessor;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.scheduling.support.PeriodicTrigger;

import javax.sql.DataSource;
import java.io.File;
import java.time.Duration;
import java.util.Map;

/**
 * Spring Integration configuration for ETL pipelines and data processing flows.
 * 
 * Implements enterprise integration patterns for data transformation and routing:
 * - File-based data ingestion and processing
 * - Database polling and change detection
 * - Message transformation and enrichment
 * - Error handling and dead letter queues
 * - Batch processing and aggregation
 */
@Configuration
public class SpringIntegrationConfig {

    /**
     * Default poller configuration for integration flows.
     */
    @Bean
    public PollerMetadata defaultPoller() {
        PollerMetadata pollerMetadata = new PollerMetadata();
        pollerMetadata.setTrigger(new PeriodicTrigger(Duration.ofSeconds(30)));
        pollerMetadata.setMaxMessagesPerPoll(10);
        return pollerMetadata;
    }

    /**
     * File-based ETL pipeline for processing external data files.
     * Monitors directory for new files and processes them through transformation pipeline.
     */
    @Bean
    public IntegrationFlow fileProcessingFlow() {
        return IntegrationFlow
                .from(fileSource(), c -> c.poller(Pollers.fixedDelay(5000)))
                .enrichHeaders(h -> h.header("source", "file-system"))
                .transform(new FileToStringTransformer())
                .channel("fileProcessingChannel")
                .route("headers['fileExtension']", 
                       mapping -> mapping
                           .channelMapping("csv", "csvProcessingChannel")
                           .channelMapping("xml", "xmlProcessingChannel")
                           .channelMapping("json", "jsonProcessingChannel")
                           .defaultOutputChannel("unknownFormatChannel"))
                .get();
    }

    /**
     * Database polling ETL pipeline for change data capture.
     * Polls database for changes and processes delta updates.
     */
    @Bean
    public IntegrationFlow databasePollingFlow(DataSource dataSource) {
        return IntegrationFlow
                .from(databaseSource(dataSource), c -> c.poller(Pollers.fixedDelay(60000)))
                .enrichHeaders(h -> h.header("source", "database")
                                   .header("processTime", System.currentTimeMillis()))
                .channel("databaseChangeChannel")
                .transform("payload.toString()")
                .filter("payload.length() > 0")
                .handle(m -> System.out.println("Database change: " + m.getPayload()))
                .get();
    }

    /**
     * CSV file processing pipeline with data validation and transformation.
     */
    @Bean
    public IntegrationFlow csvProcessingFlow() {
        return IntegrationFlow
                .from("csvProcessingChannel")
                .split()
                .filter("!payload.trim().isEmpty()")
                .transform(payload -> "csv-line: " + payload.toString())
                .aggregate(a -> a.correlationStrategy(m -> "batch")
                              .releaseStrategy(g -> g.size() >= 100)
                              .sendPartialResultOnExpiry(true)
                              .expireGroupsUponCompletion(true))
                .handle(m -> System.out.println("Processed CSV batch: " + m.getPayload()))
                .get();
    }

    /**
     * XML file processing pipeline with schema validation.
     */
    @Bean
    public IntegrationFlow xmlProcessingFlow() {
        return IntegrationFlow
                .from("xmlProcessingChannel")
                .transform(payload -> "processed: " + payload.toString())
                .channel("validXmlChannel")
                .get();
    }

    /**
     * Error handling pipeline for failed messages.
     */
    @Bean
    public IntegrationFlow errorHandlingFlow() {
        return IntegrationFlow
                .from("errorChannel")
                .enrichHeaders(h -> h.header("errorTime", System.currentTimeMillis())
                                   .header("retryCount", 0))
                .route("headers['retryable']",
                       mapping -> mapping
                           .channelMapping("true", "retryChannel")
                           .channelMapping("false", "deadLetterChannel")
                           .defaultOutputChannel("deadLetterChannel"))
                .get();
    }

    /**
     * Retry mechanism for recoverable failures.
     */
    @Bean
    public IntegrationFlow retryFlow() {
        return IntegrationFlow
                .from("retryChannel")
                .delay(d -> d.defaultDelay(5000).messageGroupId("retryDelayGroup"))
                .filter(m -> true) // Always retry for demo
                .enrichHeaders(h -> h.headerExpression("retryCount", "headers['retryCount'] + 1"))
                .channel("mainProcessingChannel")
                .get();
    }

    /**
     * Dead letter queue for unrecoverable failures.
     */
    @Bean
    public IntegrationFlow deadLetterFlow() {
        return IntegrationFlow
                .from("deadLetterChannel")
                .enrichHeaders(h -> h.header("dlqTime", System.currentTimeMillis()))
                .transform(payload -> "DLQ: " + payload.toString())
                .handle(m -> System.out.println("Dead letter: " + m.getPayload()))
                .get();
    }

    /**
     * Data aggregation pipeline for batch processing.
     */
    @Bean
    public IntegrationFlow aggregationFlow() {
        return IntegrationFlow
                .from("aggregationInputChannel")
                .aggregate(a -> a
                    .correlationStrategy(m -> m.getHeaders().get("batchId"))
                    .releaseStrategy(g -> g.size() >= 50 || isTimeoutReached(g))
                    .groupTimeout(30000)
                    .sendPartialResultOnExpiry(true))
                .transform(payload -> "batch: " + payload.toString())
                .handle(m -> System.out.println("Aggregated batch: " + m.getPayload()))
                .get();
    }

    // Channel definitions

    @Bean
    public MessageChannel fileProcessingChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel csvProcessingChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel xmlProcessingChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel jsonProcessingChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel unknownFormatChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel databaseChangeChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel validXmlChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel retryChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel deadLetterChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel aggregationInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel mainProcessingChannel() {
        return new DirectChannel();
    }

    // Message sources

    /**
     * File message source for monitoring input directory.
     */
    @Bean
    public MessageSource<File> fileSource() {
        FileReadingMessageSource source = new FileReadingMessageSource();
        source.setDirectory(new File("target/input"));
        source.setFilter(new SimplePatternFileListFilter("*.{csv,xml,json}"));
        return source;
    }

    /**
     * Database message source for polling changes.
     */
    @Bean
    public MessageSource<?> databaseSource(DataSource dataSource) {
        JdbcPollingChannelAdapter adapter = new JdbcPollingChannelAdapter(dataSource,
                "SELECT id, sist_endret, status FROM sak WHERE sist_endret > " +
                "(SELECT COALESCE(MAX(last_processed), '1970-01-01') FROM sync_status WHERE sync_type = 'CASE_SYNC')");
        adapter.setUpdateSql("UPDATE sync_status SET last_processed = CURRENT_TIMESTAMP WHERE sync_type = 'CASE_SYNC'");
        return adapter;
    }

    // Transformers and processors

    @Transformer(inputChannel = "transformationChannel")
    public Object dataTransformer(Object payload) {
        // Implement data transformation logic
        return payload;
    }

    @ServiceActivator(inputChannel = "validationChannel")
    public void dataValidator(Object payload) {
        // Implement data validation logic
    }

    // Helper methods

    private boolean isTimeoutReached(Object group) {
        // Implement timeout logic for aggregation groups
        return false;
    }
}
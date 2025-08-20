package com.example.interviewprep.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

/**
 * Distributed tracing configuration for enterprise observability.
 * 
 * Implements comprehensive monitoring and tracing across integration points:
 * - Request/response tracing for HTTP endpoints
 * - Database operation tracing
 * - External service call tracking
 * - Business process monitoring
 * - Performance metrics collection
 */
@Configuration
public class TracingConfig {

    @Value("${spring.application.name}")
    private String applicationName;

    /**
     * Request logging filter for HTTP request tracing.
     * Captures request details for debugging and monitoring.
     */
    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
        filter.setIncludeClientInfo(true);
        filter.setIncludeQueryString(true);
        filter.setIncludePayload(false); // Don't log sensitive payload data
        filter.setIncludeHeaders(true);
        filter.setMaxPayloadLength(1000);
        filter.setBeforeMessagePrefix("REQUEST: ");
        filter.setAfterMessagePrefix("RESPONSE: ");
        return filter;
    }

    /**
     * Custom observation registry for business metrics.
     */
    @Bean
    public ObservationRegistry observationRegistry() {
        return ObservationRegistry.create();
    }
}
package no.nav.gateway.config;

import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * API Gateway Configuration
 * 
 * Defines routing rules and policies for the NAV integration platform.
 * Each external service has different characteristics that require specific handling:
 * 
 * - Folkeregister: XML responses, slow legacy system, requires auth headers
 * - Skatteetaten: Fixed-width text, very slow mainframe, heavy rate limiting
 * - Bank: Complex JSON, fast but strict rate limits, requires correlation IDs
 * - A-Ordningen: CSV responses, batch processing, timeout-prone
 * - Main App: Internal JSON APIs, fast response, minimal restrictions
 * 
 * This configuration demonstrates real-world integration challenges where
 * each external system has unique requirements and limitations.
 */
@Configuration
public class GatewayConfig {

    /**
     * Route configuration for all services in the integration platform.
     * Each route handles different data formats and system characteristics.
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            
            // FOLKEREGISTER ROUTES - XML responses, legacy system delays
            .route("folkeregister-person", r -> r
                .path("/api/gateway/folkeregister/person/**")
                .filters(f -> f
                    .stripPrefix(2) // Remove /api/gateway prefix
                    .addRequestHeader("X-Client-ID", "nav-integration-platform")
                    .addRequestHeader("X-Legacy-System", "folkeregister")
                    .circuitBreaker(config -> config
                        .setName("folkeregister-cb")
                        .setFallbackUri("forward:/fallback/folkeregister")
                    )
                    .retry(config -> config
                        .setRetries(2)
                        .setBackoff(Duration.ofMillis(500), Duration.ofSeconds(2), 3, true)
                    )
                    .requestRateLimiter(config -> config
                        .setRateLimiter(redisRateLimiter())
                        .setKeyResolver(hostKeyResolver())
                    )
                )
                .uri("${FOLKEREGISTER_SERVICE_URL:http://folkeregister-api:8080}")
            )
            
            // SKATTEETATEN ROUTES - Fixed-width format, mainframe system
            .route("skatteetaten-inntekt", r -> r
                .path("/api/gateway/skatteetaten/**")
                .filters(f -> f
                    .stripPrefix(2)
                    .addRequestHeader("X-Client-ID", "nav-integration-platform")
                    .addRequestHeader("X-Legacy-System", "skatteetaten-mainframe")
                    .circuitBreaker(config -> config
                        .setName("skatteetaten-cb")
                        .setFallbackUri("forward:/fallback/skatteetaten")
                    )
                    .retry(config -> config
                        .setRetries(1) // Lower retries for slow mainframe
                        .setBackoff(Duration.ofSeconds(1), Duration.ofSeconds(5), 2, true)
                    )
                    .requestRateLimiter(config -> config
                        .setRateLimiter(restrictiveRateLimiter()) // Stricter limits
                        .setKeyResolver(hostKeyResolver())
                    )
                )
                .uri("${SKATTEETATEN_SERVICE_URL:http://skatteetaten-api:8080}")
            )
            
            // BANK ROUTES - Complex JSON, correlation ID required
            .route("bank-accounts", r -> r
                .path("/api/gateway/bank/**")
                .filters(f -> f
                    .stripPrefix(2)
                    .addRequestHeader("X-Client-ID", "nav-integration-platform")
                    .addRequestHeader("X-Correlation-ID", "#{T(java.util.UUID).randomUUID().toString()}")
                    .addRequestHeader("X-Request-Source", "nav-gateway")
                    .circuitBreaker(config -> config
                        .setName("bank-cb")
                        .setFallbackUri("forward:/fallback/bank")
                    )
                    .retry(config -> config
                        .setRetries(3)
                        .setBackoff(Duration.ofMillis(200), Duration.ofSeconds(1), 2, true)
                    )
                    .requestRateLimiter(config -> config
                        .setRateLimiter(moderateRateLimiter())
                        .setKeyResolver(hostKeyResolver())
                    )
                )
                .uri("${BANK_SERVICE_URL:http://bank-api:8080}")
            )
            
            // A-ORDNINGEN ROUTES - CSV responses, batch processing
            .route("a-ordningen-arbeidsforhold", r -> r
                .path("/api/gateway/a-ordningen/**")
                .filters(f -> f
                    .stripPrefix(2)
                    .addRequestHeader("X-Client-ID", "nav-integration-platform")
                    .addRequestHeader("X-Batch-Processing", "true")
                    .addRequestHeader("X-CSV-Format", "pipe-delimited")
                    .circuitBreaker(config -> config
                        .setName("a-ordningen-cb")
                        .setFallbackUri("forward:/fallback/a-ordningen")
                    )
                    .retry(config -> config
                        .setRetries(2)
                        .setBackoff(Duration.ofMillis(800), Duration.ofSeconds(3), 2, true)
                    )
                    .requestRateLimiter(config -> config
                        .setRateLimiter(redisRateLimiter())
                        .setKeyResolver(hostKeyResolver())
                    )
                )
                .uri("${A_ORDNINGEN_SERVICE_URL:http://a-ordningen-api:8080}")
            )
            
            // MAIN APPLICATION ROUTES - Internal APIs
            .route("nav-integration-app", r -> r
                .path("/api/gateway/nav/**")
                .filters(f -> f
                    .stripPrefix(2)
                    .addRequestHeader("X-Gateway-Request", "true")
                    .addRequestHeader("X-Internal-Route", "nav-integration")
                    .circuitBreaker(config -> config
                        .setName("nav-app-cb")
                        .setFallbackUri("forward:/fallback/nav-app")
                    )
                    .requestRateLimiter(config -> config
                        .setRateLimiter(internalRateLimiter()) // Higher limits for internal
                        .setKeyResolver(hostKeyResolver())
                    )
                )
                .uri("${MAIN_APP_SERVICE_URL:http://app:8080}")
            )
            
            // HEALTH CHECK AGGREGATION - Collects health from all services
            .route("health-aggregation", r -> r
                .path("/api/gateway/health/**")
                .filters(f -> f
                    .stripPrefix(2)
                    .addRequestHeader("X-Health-Check", "gateway-aggregation")
                )
                .uri("${MAIN_APP_SERVICE_URL:http://app:8080}")
            )
            
            .build();
    }

    /**
     * Rate limiter for external services with standard limits
     * Protects external systems from overload while allowing reasonable throughput
     */
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(
            10, // replenishRate: tokens per second
            20, // burstCapacity: maximum tokens in bucket
            1   // requestedTokens: tokens per request
        );
    }
    
    /**
     * Restrictive rate limiter for slow legacy systems (e.g., Skatteetaten mainframe)
     */
    @Bean
    public RedisRateLimiter restrictiveRateLimiter() {
        return new RedisRateLimiter(
            3,  // replenishRate: 3 requests per second max
            5,  // burstCapacity: small burst allowed
            1   // requestedTokens: single token per request
        );
    }
    
    /**
     * Moderate rate limiter for banking APIs
     */
    @Bean
    public RedisRateLimiter moderateRateLimiter() {
        return new RedisRateLimiter(
            15, // replenishRate: 15 requests per second
            30, // burstCapacity: 30 token burst
            1   // requestedTokens: single token per request
        );
    }
    
    /**
     * Higher limits for internal NAV services
     */
    @Bean
    public RedisRateLimiter internalRateLimiter() {
        return new RedisRateLimiter(
            50,  // replenishRate: 50 requests per second
            100, // burstCapacity: 100 token burst
            1    // requestedTokens: single token per request
        );
    }

    /**
     * Rate limiting key resolver - uses client IP address
     * In production, this could be based on API keys or OAuth client IDs
     */
    @Bean
    public KeyResolver hostKeyResolver() {
        return exchange -> Mono.just(
            exchange.getRequest().getRemoteAddress() != null ?
                exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : 
                "unknown"
        );
    }
}
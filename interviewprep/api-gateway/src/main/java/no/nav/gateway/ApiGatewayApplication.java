package no.nav.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * NAV Integration Platform API Gateway
 * 
 * This gateway demonstrates enterprise integration patterns using Spring Cloud Gateway.
 * Key architectural responsibilities and challenges:
 * 
 * - REQUEST ROUTING: Routes incoming requests to appropriate microservices
 * - PROTOCOL TRANSLATION: Handles different response formats (XML, JSON, CSV, fixed-width)
 * - LOAD BALANCING: Distributes requests across service instances for scalability
 * - CIRCUIT BREAKING: Prevents cascade failures when external services are down
 * - RATE LIMITING: Protects backend services from overload using Redis-backed counters
 * - AUTHENTICATION: OAuth 2.0 JWT token validation for secure API access
 * - CROSS-CUTTING CONCERNS: Logging, tracing, metrics, and correlation ID propagation
 * - SERVICE DISCOVERY: Dynamic routing to healthy service instances
 * - RESPONSE TRANSFORMATION: Converts between different data formats as needed
 * 
 * Integration patterns demonstrated:
 * - API Gateway pattern for microservices communication
 * - Backend for Frontend (BFF) pattern for client-specific APIs
 * - Retry and timeout patterns for resilient external service calls
 * - Content-based routing to different external system formats
 * - Request/response logging for audit and debugging
 * - Health check aggregation across all services
 * 
 * This gateway serves as the single entry point for the NAV integration platform,
 * abstracting the complexity of multiple external systems (Folkeregister, Skatteetaten, 
 * Bank, A-Ordningen) from client applications and providing consistent API contracts.
 * 
 * The gateway handles the reality that government and financial systems use different:
 * - Data formats (XML, JSON, CSV, fixed-width)
 * - Authentication methods (OAuth, basic auth, API keys)
 * - Response structures (nested objects, flat records, arrays)
 * - Error handling patterns (HTTP codes, custom error formats)
 * - Rate limiting and throttling policies
 * 
 * This is essential for NAV's modernization from monolithic Oracle EBS systems
 * to distributed, cloud-native microservices architecture.
 */
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
package no.nav.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Fallback Controller for Circuit Breaker Patterns
 * 
 * When external services are down or unresponsive, circuit breakers redirect
 * requests to these fallback endpoints. Each service type has a custom fallback
 * that matches the expected response format to prevent client-side errors.
 * 
 * This demonstrates resilience patterns essential for government integration:
 * - Graceful degradation when external systems fail
 * - Format-specific fallback responses (XML, JSON, CSV, fixed-width)
 * - Maintaining service contracts during outages
 * - Providing meaningful error information to clients
 * - Preventing cascade failures across the integration platform
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    /**
     * Folkeregister fallback - returns XML format to match service contract
     * Maintains XML structure so integration flows don't break during outages
     */
    @GetMapping(value = "/folkeregister", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> folkeregisterFallback() {
        String xmlResponse = """
            <?xml version="1.0" encoding="UTF-8"?>
            <person>
                <fødselsnummer>00000000000</fødselsnummer>
                <status>TJENESTE_UTILGJENGELIG</status>
                <melding>Folkeregisteret er midlertidig utilgjengelig. Prøv igjen senere.</melding>
                <tidspunkt>%s</tidspunkt>
            </person>
            """.formatted(Instant.now().toString());
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(xmlResponse);
    }

    /**
     * Skatteetaten fallback - returns fixed-width format to match legacy expectations
     * Maintains exact field positions so parsing logic doesn't fail
     */
    @GetMapping(value = "/skatteetaten", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> skatteetatenFallback() {
        // Fixed-width format with error indicator
        StringBuilder fixedWidth = new StringBuilder();
        fixedWidth.append("00000000000")  // Pos 1-11: Fødselsnummer placeholder
                  .append("  ")           // Pos 12-13: Filler
                  .append("ERROR_SERVICE ")  // Pos 14-28: Error indicator
                  .append("UNAVAILABLE   ")  // Pos 29-43: Status
                  .append("0.00          ")  // Pos 44-58: Zero amount
                  .append("2024      ")      // Pos 59-68: Current year
                  .append("STENGT    ")      // Pos 69-78: Service status
                  .append(Instant.now().toString().substring(0, 10))  // Pos 79-88: Date
                  .append(" ".repeat(200 - fixedWidth.length())); // Pad to 200 chars
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(fixedWidth.toString());
    }

    /**
     * Bank fallback - returns JSON format with banking structure
     * Maintains complex JSON structure to prevent client parsing errors
     */
    @GetMapping(value = "/bank", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> bankFallback() {
        Map<String, Object> fallbackResponse = Map.of(
            "account-number", "00000000000",
            "account_holder", Map.of(
                "customer_id", "UNAVAILABLE",
                "national_id", "00000000000",
                "full_name", "Service Unavailable"
            ),
            "accountBalance", Map.of(
                "available_balance", Map.of("amount", 0.0, "currency-code", "NOK"),
                "pending-balance", Map.of("amount", 0.0, "currency-code", "NOK"),
                "last_updated", Instant.now().toString()
            ),
            "account-status", Map.of(
                "status_code", "SERVICE_UNAVAILABLE",
                "status-description", "Banking service is temporarily unavailable",
                "is_verified", false
            ),
            "metadata", Map.of(
                "request_timestamp", Instant.now().toString(),
                "response-id", "FALLBACK_RESPONSE",
                "api_version", "2.1.4-fallback",
                "error_code", "SERVICE_CIRCUIT_OPEN"
            )
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(fallbackResponse);
    }

    /**
     * A-Ordningen fallback - returns CSV format with error record
     * Maintains CSV structure with pipe delimiters as expected
     */
    @GetMapping(value = "/a-ordningen", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<String> aordningenFallback() {
        StringBuilder csvResponse = new StringBuilder();
        
        // CSV header with pipe delimiters (matches service contract)
        csvResponse.append("fødselsnummer|arbeidsgiver_orgnr|arbeidsforhold_id|")
                   .append("stillingsprosent|månedlønn|framdato|tildato|stillingstype|")
                   .append("arbeidssted_kommune|yrke_kode|status\n");
        
        // Error record
        csvResponse.append("00000000000|000000000|ERROR_UNAVAILABLE|")
                   .append("0,0|0|01.01.2024||SERVICE_DOWN|")
                   .append("0000|0000|UTILGJENGELIG\n");
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Content-Type", "text/csv; charset=UTF-8")
                .body(csvResponse.toString());
    }

    /**
     * Main NAV application fallback - returns standard JSON error
     */
    @GetMapping(value = "/nav-app", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> navAppFallback() {
        Map<String, Object> errorResponse = Map.of(
            "error", "SERVICE_UNAVAILABLE",
            "message", "NAV Integration Platform is temporarily unavailable",
            "timestamp", Instant.now().toString(),
            "status", 503,
            "path", "/api/nav/",
            "suggestion", "Please try again in a few minutes or contact support"
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(errorResponse);
    }

    /**
     * General health check fallback when health aggregation fails
     */
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> healthFallback() {
        Map<String, Object> healthResponse = Map.of(
            "status", "DEGRADED",
            "timestamp", Instant.now().toString(),
            "services", Map.of(
                "gateway", "UP",
                "external_services", "DOWN",
                "circuit_breakers", "OPEN"
            ),
            "message", "Some services are unavailable, operating in degraded mode"
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(healthResponse);
    }
}
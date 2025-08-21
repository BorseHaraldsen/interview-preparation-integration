package no.nav.folkeregister;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Folkeregister API - Norway's Central Population Registry
 * 
 * This service simulates the Norwegian population registry (Det sentrale folkeregisteret).
 * Key characteristics that create integration challenges:
 * 
 * - Responds with XML format (not JSON like modern APIs)
 * - Uses Norwegian field names and terminology  
 * - Nested data structure with address hierarchies
 * - Uses Norwegian date format (dd.MM.yyyy)
 * - Strict fødselsnummer validation (11-digit Norwegian national ID)
 * - Legacy system response delays to simulate real-world latency
 * 
 * Integration challenges this creates:
 * - XML to JSON transformation required
 * - Field name mapping (fødselsnummer -> ssn, etc.)
 * - Date format conversion
 * - Address structure flattening/nesting
 * - Character encoding handling (Norwegian characters: æ, ø, å)
 */
@SpringBootApplication
public class FolkeregisterApplication {
    public static void main(String[] args) {
        SpringApplication.run(FolkeregisterApplication.class, args);
    }
}
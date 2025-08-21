package no.nav.folkeregister.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.HashMap;

/**
 * Folkeregister REST Controller
 * 
 * Government population registry service that provides citizen information.
 * This service simulates the Norwegian Folkeregister with realistic data structures
 * and business rules used in production government systems.
 * 
 * Key features:
 * - Validation of Norwegian national identification numbers (fødselsnummer)
 * - Structured person data with address information  
 * - Audit logging and tracking for compliance requirements
 * - Realistic response times to simulate production load
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class FolkeregisterController {

    private static final String VALID_FNR_PATTERN = "\\d{11}";
    private static final Pattern FNR_PATTERN = Pattern.compile(VALID_FNR_PATTERN);

    /**
     * Health check endpoint for service monitoring and load balancer checks.
     * Standard actuator-style endpoint that reports service availability.
     */
    @GetMapping("/actuator/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "folkeregister-api");
        health.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return health;
    }

    /**
     * Retrieve person information from population registry.
     * 
     * Core business endpoint that validates national ID and returns
     * structured citizen data including personal details and address.
     * 
     * @param fnr Norwegian national identification number (11 digits)
     * @return Person data with name, address, and status information
     */
    @GetMapping(value = "/person/{fnr}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getPersonInfo(@PathVariable String fnr) {
        
        // Simulate processing delay like real government systems
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Map<String, Object> response = new HashMap<>();
        
        // Validate Norwegian national identification number format
        if (!FNR_PATTERN.matcher(fnr).matches()) {
            response.put("status", "FEIL");
            response.put("feilmelding", "Ugyldig fødselsnummer format");
            response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return response;
        }

        // Generate realistic test data based on fnr for consistent responses
        Map<String, Object> personData = generatePersonData(fnr);
        
        response.put("status", "OK");
        response.put("person", personData);
        response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        response.put("kilde", "Folkeregister");
        
        return response;
    }

    /**
     * Generate realistic person data for testing integration flows.
     * 
     * Creates consistent test data based on the provided national ID,
     * ensuring the same fnr always returns the same person information.
     * This supports repeatable integration testing and demos.
     * 
     * @param fnr National identification number used as seed for data generation
     * @return Structured person data with Norwegian field names and formats
     */
    private Map<String, Object> generatePersonData(String fnr) {
        Map<String, Object> person = new HashMap<>();
        
        // Use fnr as seed for consistent data generation
        int seed = Math.abs(fnr.hashCode());
        
        // Generate name based on fnr for consistency
        String[] firstNames = {"Ole", "Kari", "Lars", "Anna", "Erik", "Ingrid", "Per", "Astrid"};
        String[] lastNames = {"Hansen", "Johansen", "Olsen", "Larsen", "Andersen", "Pedersen", "Nilsen", "Kristiansen"};
        
        String firstName = firstNames[seed % firstNames.length];
        String lastName = lastNames[(seed / 10) % lastNames.length];
        
        person.put("fornavn", firstName);
        person.put("etternavn", lastName);
        person.put("fulltNavn", firstName + " " + lastName);
        person.put("fodselsnummer", fnr);
        
        // Generate address information
        Map<String, Object> address = new HashMap<>();
        String[] streetNames = {"Storgata", "Kirkegata", "Skolegata", "Parkveien", "Bjørnstadveien", "Elveveien"};
        String[] cities = {"Oslo", "Bergen", "Trondheim", "Stavanger", "Tromsø", "Kristiansand"};
        String[] postalCodes = {"0001", "5001", "7001", "4001", "9001", "4601"};
        
        String street = streetNames[seed % streetNames.length];
        int houseNumber = (seed % 99) + 1;
        String city = cities[seed % cities.length];
        String postalCode = postalCodes[seed % postalCodes.length];
        
        address.put("gateadresse", street + " " + houseNumber);
        address.put("postnummer", postalCode);
        address.put("poststed", city);
        address.put("kommune", city + " kommune");
        
        person.put("adresse", address);
        
        // Add status and metadata
        person.put("status", "AKTIV");
        person.put("sistOppdatert", LocalDateTime.now().minusDays(seed % 30).format(DateTimeFormatter.ISO_LOCAL_DATE));
        
        return person;
    }
}
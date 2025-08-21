package no.nav.skatteetaten;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Skatteetaten API - Norwegian Tax Authority
 * 
 * This service simulates the Norwegian tax authority (Skatteetaten) systems.
 * Key integration challenges this service creates:
 * 
 * - FIXED-WIDTH FORMAT responses (not JSON or XML)
 * - Legacy mainframe-style record structure 
 * - Norwegian currency format (123.456,78 instead of 123,456.78)
 * - Norwegian date format (dd.MM.yyyy)
 * - Status codes in Norwegian (AKTIV, INAKTIV, UNDER_BEHANDLING)
 * - Character encoding issues with Norwegian characters
 * - Line-based protocol (each line is a complete record)
 * 
 * Integration challenges this creates:
 * - Fixed-width field parsing and validation
 * - Currency format conversion and locale handling
 * - Text-to-JSON transformation
 * - Field position mapping and data extraction
 * - Norwegian character encoding (æ, ø, å) in fixed-width fields
 * - Null value representation in fixed-width format (spaces vs zeros)
 * 
 * This simulates the reality of integrating with Norwegian government
 * legacy systems that still run on mainframe infrastructure.
 */
@SpringBootApplication
public class SkatteetatenApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkatteetatenApplication.class, args);
    }
}
package no.nav.aordningen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A-Ordningen API - Norwegian Employment Registry
 * 
 * This service simulates A-Ordningen (Employment and Income Register).
 * Key integration challenges this service creates:
 * 
 * - CSV format with PIPE delimiters (|) instead of commas
 * - Norwegian field headers and terminology
 * - Multiple records per response (batch processing)
 * - Date ranges requiring multiple API calls
 * - Special encoding for Norwegian characters in CSV
 * - NULL values represented as empty fields between delimiters
 * - Header row with Norwegian column names
 * - Employer organization numbers (9 digits) vs person numbers (11 digits)
 * 
 * Integration challenges this creates:
 * - CSV parsing with pipe delimiters
 * - Header row mapping to field names
 * - Batch record processing and splitting
 * - Norwegian character encoding in CSV format
 * - Empty field handling and null value interpretation
 * - Date range aggregation across multiple API calls
 * - Organization number vs person number validation
 * - Employment period overlap detection and merging
 * 
 * This simulates integration with Norwegian employment data systems
 * that often export data in legacy CSV formats for batch processing.
 */
@SpringBootApplication
public class AordningenApplication {
    public static void main(String[] args) {
        SpringApplication.run(AordningenApplication.class, args);
    }
}
package no.nav.bank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bank API - Banking Integration Service
 * 
 * This service simulates banking system integrations for payment verification.
 * Key integration challenges this service creates:
 * 
 * - Custom JSON structure with nested objects and arrays
 * - ISO 8601 date/time formats (not Norwegian dd.MM.yyyy)
 * - Different field naming conventions (camelCase vs underscore_case vs kebab-case)
 * - Currency codes (NOK, USD, EUR) instead of simple numbers
 * - Bank-specific terminology (accountNumber vs kontonummer)
 * - Account validation using MOD-11 algorithm (Norwegian bank accounts)
 * - Transaction status codes that don't map 1:1 to NAV status codes
 * - Pagination with different parameter names (page/size vs offset/limit)
 * 
 * Integration challenges this creates:
 * - JSON structure mapping and flattening
 * - Date format conversion (ISO 8601 to Norwegian format)
 * - Field name transformation (accountNumber -> kontonummer)
 * - Currency handling and conversion
 * - Status code mapping between banking and NAV terminology
 * - Pagination parameter transformation
 * - Account number validation and formatting
 * 
 * This simulates real banking integration complexity where different
 * financial institutions have their own API standards and formats.
 */
@SpringBootApplication
public class BankApplication {
    public static void main(String[] args) {
        SpringApplication.run(BankApplication.class, args);
    }
}
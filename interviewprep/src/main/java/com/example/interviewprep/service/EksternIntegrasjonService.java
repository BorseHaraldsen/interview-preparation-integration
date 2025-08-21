package com.example.interviewprep.service;

import com.example.interviewprep.models.Bruker;
import com.example.interviewprep.models.SaksType;
import com.example.interviewprep.models.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for integrasjon med eksterne systemer - med DTOs
 * 
 * OPPDATERT: Bruker nå egne DTO-klasser i models/dto pakken
 */
@Service
public class EksternIntegrasjonService {

    private static final Logger logger = LoggerFactory.getLogger(EksternIntegrasjonService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Direct external service URLs for development and testing
    // These point to our actual microservices running on local ports
    private static final String FOLKEREGISTER_BASE_URL = "http://localhost:8091/api/v1";
    private static final String A_ORDNINGEN_BASE_URL = "http://localhost:8094/api/v1";
    private static final String BANK_VALIDERING_BASE_URL = "http://localhost:8093/api/v1";
    private static final String SKATTEETATEN_BASE_URL = "http://localhost:8092/api/v1";

    @Autowired
    public EksternIntegrasjonService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Hent person-informasjon fra Folkeregister - XML FORMAT
     * 
     * KRITISK INTEGRASJON: All NAV-saksbehandling avhenger av korrekt person-data
     * INTEGRASJONSCOMPLEXITY: Folkeregister returnerer XML med norske feltnavn
     * som må transformeres til vårt interne JSON-format
     * 
     * @Retryable: Spring Retry - prøver på nytt ved feil
     * - maxAttempts: Maksimalt 3 forsøk
     * - backoff: Venter 1 sek, deretter 2 sek, deretter 4 sek
     * - include: Hvilke exceptions som trigger retry
     */
    @Retryable(
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2),
        include = {HttpServerErrorException.class, HttpClientErrorException.class}
    )
    public FolkeregisterData hentPersonFraFolkeregister(String fnr) {
        logger.info("Henter persondata fra Folkeregister for person - XML format via API Gateway");

        try {
            // 1. SIKKERHET - Bygg request med authentication og API Gateway headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/xml"); // Folkeregister returns XML
            headers.set("X-Request-ID", UUID.randomUUID().toString()); // Request tracing
            headers.set("X-Consumer-ID", "NAV-INTEGRATION-PLATFORM"); // Client identification
            headers.set("X-Legacy-System", "folkeregister"); // System identification for gateway

            HttpEntity<String> request = new HttpEntity<>(headers);

            // 2. API KALL - GET til Folkeregister via API Gateway
            String url = FOLKEREGISTER_BASE_URL + "/api/folkeregister/person/" + fnr;
            
            logger.debug("Calling Folkeregister via API Gateway: {}", url);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                request, 
                String.class
            );

            // 3. XML RESPONS PROSESSERING - Transform from XML to internal format
            if (response.getStatusCode() == HttpStatus.OK) {
                String xmlResponse = response.getBody();
                logger.debug("Received XML response from Folkeregister: {}", xmlResponse);
                
                // Parse XML and extract Norwegian field names to internal format
                FolkeregisterData personData = parseXmlFolkeregisterResponse(xmlResponse, fnr);

                logger.info("Successfully parsed XML person data from Folkeregister");
                return personData;
            } else {
                logger.warn("Unexpected status from Folkeregister via API Gateway: {}", response.getStatusCode());
                throw new EksternIntegrasjonException("Folkeregister returned status: " + response.getStatusCode());
            }

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                logger.warn("Person ikke funnet i Folkeregister");
                throw new PersonIkkeFunnetException("Person ikke registrert i Folkeregister");
            } else if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                logger.error("Ikke tilgang til Folkeregister - sjekk tokens og rettigheter");
                throw new TilgangNektetException("Ingen tilgang til Folkeregister");
            }
            logger.error("Client error ved Folkeregister kall: {}", e.getMessage());
            throw new EksternIntegrasjonException("Folkeregister client error", e);
            
        } catch (HttpServerErrorException e) {
            logger.error("Server error ved Folkeregister kall: {}", e.getMessage());
            throw new EksternIntegrasjonException("Folkeregister server error", e);
            
        } catch (Exception e) {
            logger.error("Uventet feil ved Folkeregister integrasjon: {}", e.getMessage());
            throw new EksternIntegrasjonException("Generell Folkeregister feil", e);
        }
    }

    /**
     * Hent arbeidsforhold fra A-ordningen - CSV FORMAT med PIPE delimiters
     * 
     * KRITISK FOR DAGPENGER: Arbeidshistorikk bestemmer rettigheter
     * INTEGRATION COMPLEXITY: A-ordningen returnerer CSV med pipe delimiters (|)
     * og norske feltnavn som må parsers og transformeres til vårt format
     */
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public List<ArbeidsforholdData> hentArbeidsforholdFraAOrdningen(String fnr, LocalDateTime fradato) {
        logger.info("Henter arbeidsforhold fra A-ordningen for person siden {} - CSV format via API Gateway", fradato);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "text/csv; charset=UTF-8"); // A-ordningen returns CSV
            headers.set("X-Request-ID", UUID.randomUUID().toString());
            headers.set("X-Consumer-ID", "NAV-INTEGRATION-PLATFORM");
            headers.set("X-Batch-Processing", "true"); // Indicates batch processing
            headers.set("X-CSV-Format", "pipe-delimited"); // Format specification

            // Query parameters with Norwegian parameter names (as expected by A-ordningen)
            String url = A_ORDNINGEN_BASE_URL + "/api/aordningen/arbeidsforhold/" + fnr 
                        + "?framdato=" + fradato.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));

            HttpEntity<String> request = new HttpEntity<>(headers);
            
            logger.debug("Calling A-ordningen via API Gateway: {}", url);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, request, String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                String csvResponse = response.getBody();
                logger.debug("Received CSV response from A-ordningen: {}", csvResponse);
                
                // Parse CSV with pipe delimiters and transform to internal format
                List<ArbeidsforholdData> arbeidsforhold = parseCsvArbeidsforholdResponse(csvResponse, fnr);

                logger.info("Successfully parsed {} arbeidsforhold from A-ordningen CSV", arbeidsforhold.size());
                return arbeidsforhold;
            }

            throw new EksternIntegrasjonException("A-ordningen returned status: " + response.getStatusCode());

        } catch (Exception e) {
            logger.error("Error retrieving arbeidsforhold from A-ordningen: {}", e.getMessage());
            throw new EksternIntegrasjonException("A-ordningen integration failed", e);
        }
    }

    /**
     * Valider bankkonto for utbetaling - COMPLEX JSON FORMAT
     * 
     * KRITISK FOR UTBETALINGER: Må sikre at penger går til rett konto
     * INTEGRATION COMPLEXITY: Bank API returnerer kompleks JSON med nested struktur
     * og mixed naming conventions som må navigeres og transformeres
     */
    public boolean validerBankkonto(String kontonummer, String fnr) {
        logger.info("Validerer bankkonto for utbetaling - Complex JSON format via API Gateway");

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json"); // Bank returns complex JSON
            headers.set("Content-Type", "application/json");
            headers.set("X-Request-ID", UUID.randomUUID().toString());
            headers.set("X-Consumer-ID", "NAV-INTEGRATION-PLATFORM");
            headers.set("X-Correlation-ID", UUID.randomUUID().toString()); // Bank requires correlation ID
            headers.set("X-Request-Source", "nav-integration");

            // Bank API uses different parameter structure
            String url = BANK_VALIDERING_BASE_URL + "/api/banking/accounts/" + kontonummer;
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            logger.debug("Calling Bank API via API Gateway: {}", url);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, request, String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                String jsonResponse = response.getBody();
                logger.debug("Received complex JSON response from Bank API: {}", jsonResponse);
                
                // Parse complex JSON and validate account ownership
                boolean gyldig = parseComplexJsonBankResponse(jsonResponse, kontonummer, fnr);
                
                logger.info("Bank account validation result: {}", gyldig ? "VALID" : "INVALID");
                return gyldig;
            }

            logger.warn("Bank API returned status: {}", response.getStatusCode());
            return false;

        } catch (Exception e) {
            logger.error("Error validating bank account: {}", e.getMessage());
            // Return false for security - better to reject than allow invalid account
            return false;
        }
    }

    /**
     * Hent inntektsdata fra Skatteetaten - FIXED-WIDTH FORMAT
     * 
     * KRITISK FOR YTELSESBEREGNING: Inntekt bestemmer utbetalingsbeløp
     * INTEGRATION COMPLEXITY: Skatteetaten returnerer fixed-width format fra mainframe
     * med 200-character records som må parses posisjon-for-posisjon
     */
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public InntektsData hentInntektFraSkatteetaten(String fnr, int aar) {
        logger.info("Henter inntektsdata fra Skatteetaten for år {} - Fixed-width format via API Gateway", aar);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "text/plain"); // Skatteetaten returns fixed-width text
            headers.set("X-Request-ID", UUID.randomUUID().toString());
            headers.set("X-Consumer-ID", "NAV-INTEGRATION-PLATFORM");
            headers.set("X-Legacy-System", "skatteetaten-mainframe");
            headers.set("X-Mainframe-Processing", "true"); // Indicates mainframe system

            String url = SKATTEETATEN_BASE_URL + "/api/skatt/person/" + fnr + "/inntekt/" + aar;
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            logger.debug("Calling Skatteetaten mainframe via API Gateway: {}", url);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, request, String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                String fixedWidthResponse = response.getBody();
                logger.debug("Received fixed-width response from Skatteetaten: {}", fixedWidthResponse);
                
                // Parse fixed-width format and transform to internal structure
                InntektsData inntekt = parseFixedWidthSkatteetatenResponse(fixedWidthResponse, fnr, aar);

                logger.info("Successfully parsed fixed-width income data from Skatteetaten");
                return inntekt;
            }

            throw new EksternIntegrasjonException("Skatteetaten returned status: " + response.getStatusCode());

        } catch (Exception e) {
            logger.error("Error retrieving income data from Skatteetaten: {}", e.getMessage());
            throw new EksternIntegrasjonException("Skatteetaten integration failed", e);
        }
    }

    /**
     * Sammenstill data fra multiple kilder for saksbehandling
     * 
     * INTEGRASJONSMØNSTER: Aggregator Pattern
     * Henter data fra flere systemer og sammenstiller til helhetlig bilde
     */
    public SaksgrunnlagData sammenstillSaksgrunnlag(String fnr, SaksType saksType) {
        logger.info("Sammenstiller saksgrunnlag for {} sak", saksType);

        try {
            // 1. Hent grunnleggende persondata (alltid nødvendig)
            FolkeregisterData person = hentPersonFraFolkeregister(fnr);
            
            // 2. Hent arbeidshistorikk (hvis relevant for sakstype)
            List<ArbeidsforholdData> arbeidsforhold = new ArrayList<>();
            if (saksType == SaksType.DAGPENGER || saksType == SaksType.AAP) {
                arbeidsforhold = hentArbeidsforholdFraAOrdningen(fnr, LocalDateTime.now().minusYears(3));
            }
            
            // 3. Hent inntektsdata (for ytelsesberegning)
            InntektsData inntekt = null;
            if (saksType != SaksType.BARNETRYGD) { // Barnetrygd er ikke inntektsavhengig
                try {
                    inntekt = hentInntektFraSkatteetaten(fnr, LocalDateTime.now().getYear() - 1);
                } catch (Exception e) {
                    logger.warn("Kunne ikke hente inntektsdata - fortsetter uten: {}", e.getMessage());
                }
            }

            SaksgrunnlagData saksgrunnlag = new SaksgrunnlagData(
                person, arbeidsforhold, inntekt, LocalDateTime.now()
            );

            logger.info("Saksgrunnlag sammenstilt OK - {} arbeidsforhold, inntekt: {}", 
                       arbeidsforhold.size(), inntekt != null ? "JA" : "NEI");

            return saksgrunnlag;

        } catch (Exception e) {
            logger.error("Feil ved sammenstilling av saksgrunnlag: {}", e.getMessage());
            throw new EksternIntegrasjonException("Kunne ikke sammenstille saksgrunnlag", e);
        }
    }

    /**
     * Parse XML response from Folkeregister and transform to internal format
     * 
     * INTEGRATION COMPLEXITY: Transform Norwegian XML field names to internal JSON structure
     * - fødselsnummer -> fodselsnummer
     * - personnavn.fulltNavn -> navn  
     * - bostedsadresse -> adresse (flattened from nested structure)
     * - sivilstand -> sivilstand
     * - status.erDød -> doedsfall
     */
    private FolkeregisterData parseXmlFolkeregisterResponse(String xmlResponse, String expectedFnr) {
        try {
            // Simple XML parsing for demo - in production would use proper XML parser
            logger.debug("Parsing Folkeregister XML response");
            
            String fødselsnummer = extractXmlValue(xmlResponse, "fødselsnummer");
            String fulltNavn = extractXmlValue(xmlResponse, "fulltNavn");
            String sivilstand = extractXmlValue(xmlResponse, "sivilstand");
            
            // Extract nested address information and flatten it
            String gatenavn = extractXmlValue(xmlResponse, "gatenavn");
            String husnummer = extractXmlValue(xmlResponse, "husnummer");
            String postnummer = extractXmlValue(xmlResponse, "postnummer");
            String poststedsnavn = extractXmlValue(xmlResponse, "poststedsnavn");
            
            String fullAdresse = String.format("%s %s, %s %s", 
                                               gatenavn != null ? gatenavn : "",
                                               husnummer != null ? husnummer : "",
                                               postnummer != null ? postnummer : "",
                                               poststedsnavn != null ? poststedsnavn : "").trim();
            
            // Extract death status
            String erDødStr = extractXmlValue(xmlResponse, "erDød");
            boolean erDød = "true".equalsIgnoreCase(erDødStr);
            
            logger.info("Transformed XML data: navn={}, adresse={}, sivilstand={}, død={}", 
                       fulltNavn, fullAdresse, sivilstand, erDød);
            
            return new FolkeregisterData(
                fødselsnummer != null ? fødselsnummer : expectedFnr,
                fulltNavn != null ? fulltNavn : "Ukjent navn",
                fullAdresse.isEmpty() ? "Ukjent adresse" : fullAdresse,
                sivilstand != null ? sivilstand : "UGIFT",
                erDød
            );
            
        } catch (Exception e) {
            logger.error("Failed to parse XML response from Folkeregister: {}", e.getMessage());
            throw new EksternIntegrasjonException("XML parsing failed for Folkeregister response", e);
        }
    }
    
    /**
     * Extract XML element value by tag name - simple implementation for demo
     * In production, would use proper XML parser like Jackson XML or JAXB
     */
    private String extractXmlValue(String xml, String tagName) {
        String startTag = "<" + tagName + ">";
        String endTag = "</" + tagName + ">";
        
        int startIndex = xml.indexOf(startTag);
        if (startIndex == -1) return null;
        
        startIndex += startTag.length();
        int endIndex = xml.indexOf(endTag, startIndex);
        if (endIndex == -1) return null;
        
        return xml.substring(startIndex, endIndex).trim();
    }
    
    /**
     * Parse CSV response from A-ordningen and transform to internal format
     * 
     * CSV FORMAT: fødselsnummer|arbeidsgiver_orgnr|arbeidsforhold_id|stillingsprosent|månedlønn|framdato|tildato|stillingstype|arbeidssted_kommune|yrke_kode|status
     * 
     * INTEGRATION COMPLEXITY: 
     * - Pipe delimiters (|) instead of standard commas
     * - Norwegian field names and date formats (dd.MM.yyyy)
     * - Norwegian decimal format with comma (12,5 instead of 12.5)
     * - Empty fields for null values
     */
    private List<ArbeidsforholdData> parseCsvArbeidsforholdResponse(String csvResponse, String expectedFnr) {
        List<ArbeidsforholdData> arbeidsforhold = new ArrayList<>();
        
        try {
            String[] lines = csvResponse.split("\n");
            boolean isFirstLine = true;
            
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                // Skip header row (first line with field names)
                if (isFirstLine) {
                    isFirstLine = false;
                    logger.debug("CSV header: {}", line);
                    continue;
                }
                
                // Split by pipe delimiter
                String[] fields = line.split("\\|", -1); // -1 keeps empty fields
                
                if (fields.length >= 7) { // Minimum required fields
                    try {
                        String fødselsnummer = fields[0];
                        String arbeidsgiverOrgnr = fields[1];
                        String arbeidsforholdId = fields[2];
                        String stillingsprosent = fields[3];
                        String månedlønn = fields[4];
                        String framdato = fields[5];
                        String tildato = fields[6].isEmpty() ? null : fields[6]; // null if empty
                        String stillingstype = fields.length > 7 ? fields[7] : "UKJENT";
                        
                        // Transform Norwegian decimal format (12,5 -> 12.5)
                        double prosent = 100.0; // default
                        if (!stillingsprosent.isEmpty()) {
                            prosent = Double.parseDouble(stillingsprosent.replace(',', '.'));
                        }
                        
                        // Transform Norwegian date format (dd.MM.yyyy -> internal format)
                        String startdato = transformNorwegianDate(framdato);
                        String sluttdato = tildato != null ? transformNorwegianDate(tildato) : null;
                        
                        ArbeidsforholdData arbeidsforhold1 = new ArbeidsforholdData(
                            arbeidsgiverOrgnr,
                            startdato,
                            sluttdato,
                            stillingstype,
                            prosent
                        );
                        
                        arbeidsforhold.add(arbeidsforhold1);
                        logger.debug("Parsed arbeidsforhold: orgnr={}, type={}, prosent={}", 
                                   arbeidsgiverOrgnr, stillingstype, prosent);
                        
                    } catch (Exception e) {
                        logger.warn("Failed to parse CSV line: {} - Error: {}", line, e.getMessage());
                        // Continue with next line rather than failing entire response
                    }
                } else {
                    logger.warn("CSV line has insufficient fields: {}", line);
                }
            }
            
            logger.info("Successfully parsed {} arbeidsforhold from CSV", arbeidsforhold.size());
            
        } catch (Exception e) {
            logger.error("Failed to parse CSV response from A-ordningen: {}", e.getMessage());
            throw new EksternIntegrasjonException("CSV parsing failed for A-ordningen response", e);
        }
        
        return arbeidsforhold;
    }
    
    /**
     * Transform Norwegian date format (dd.MM.yyyy) to internal format (yyyy-MM-dd)
     */
    private String transformNorwegianDate(String norwegianDate) {
        if (norwegianDate == null || norwegianDate.trim().isEmpty()) {
            return null;
        }
        
        try {
            // Parse dd.MM.yyyy and convert to yyyy-MM-dd
            String[] parts = norwegianDate.split("\\.");
            if (parts.length == 3) {
                return parts[2] + "-" + parts[1] + "-" + parts[0]; // yyyy-MM-dd
            }
        } catch (Exception e) {
            logger.warn("Failed to transform date {}: {}", norwegianDate, e.getMessage());
        }
        
        return norwegianDate; // Return as-is if transformation fails
    }
    
    /**
     * Parse fixed-width response from Skatteetaten and transform to internal format
     * 
     * FIXED-WIDTH FORMAT (200 chars total):
     * Pos 1-11:   Fødselsnummer (11 chars)
     * Pos 12-13:  Filler spaces (2 chars)
     * Pos 14-28:  Inntekt hovedjobb (15 chars, Norwegian decimal format)
     * Pos 29-43:  Inntekt bijobb (15 chars, Norwegian decimal format)
     * Pos 44-58:  Skatt betalt (15 chars, Norwegian decimal format)
     * Pos 59-68:  Skatteår (10 chars)
     * Pos 69-78:  Status (10 chars)
     * Pos 79-88:  Siste endring (10 chars, dd.MM.yyyy)
     * Pos 89-200: Filler spaces (112 chars)
     * 
     * INTEGRATION COMPLEXITY:
     * - Fixed position parsing (exact character positions)
     * - Norwegian decimal format with comma (123.456,78)
     * - Status codes in Norwegian (AKTIV, INAKTIV, etc.)
     * - Date format conversion from dd.MM.yyyy
     */
    private InntektsData parseFixedWidthSkatteetatenResponse(String fixedWidthResponse, String expectedFnr, int expectedYear) {
        try {
            logger.debug("Parsing Skatteetaten fixed-width response (200 chars)");
            
            if (fixedWidthResponse == null || fixedWidthResponse.length() < 88) {
                throw new IllegalArgumentException("Fixed-width response too short");
            }
            
            // Extract fields by exact position
            String fødselsnummer = fixedWidthResponse.substring(0, 11).trim();
            String inntektHovedjobbStr = fixedWidthResponse.substring(13, 28).trim();
            String inntektBijobbStr = fixedWidthResponse.substring(28, 43).trim();
            String skattBetaltStr = fixedWidthResponse.substring(43, 58).trim();
            String skatteårStr = fixedWidthResponse.substring(58, 68).trim();
            String statusStr = fixedWidthResponse.substring(68, 78).trim();
            String sisteEndringStr = fixedWidthResponse.substring(78, 88).trim();
            
            // Check for error records
            if (inntektHovedjobbStr.startsWith("ERROR:") || statusStr.equals("STENGT")) {
                throw new EksternIntegrasjonException("Skatteetaten returned error: " + statusStr);
            }
            
            // Parse Norwegian decimal format (123.456,78 -> 123456.78)
            double inntektHovedjobb = parseNorwegianCurrency(inntektHovedjobbStr);
            double inntektBijobb = parseNorwegianCurrency(inntektBijobbStr);
            double skattBetalt = parseNorwegianCurrency(skattBetaltStr);
            
            // Calculate total brutto inntekt
            double bruttoInntekt = inntektHovedjobb + inntektBijobb;
            double skattepliktigInntekt = bruttoInntekt - skattBetalt; // Simplified calculation
            
            logger.info("Parsed fixed-width tax data: brutto={}, skattepliktig={}, year={}, status={}", 
                       bruttoInntekt, skattepliktigInntekt, skatteårStr, statusStr);
            
            return new InntektsData(
                expectedFnr,
                expectedYear,
                bruttoInntekt,
                skattepliktigInntekt,
                0.0 // Pension income not provided in this format
            );
            
        } catch (Exception e) {
            logger.error("Failed to parse fixed-width response from Skatteetaten: {}", e.getMessage());
            throw new EksternIntegrasjonException("Fixed-width parsing failed for Skatteetaten response", e);
        }
    }
    
    /**
     * Parse Norwegian currency format (123.456,78) to double
     * Handle both grouped and non-grouped formats
     */
    private double parseNorwegianCurrency(String norwegianAmount) {
        if (norwegianAmount == null || norwegianAmount.trim().isEmpty()) {
            return 0.0;
        }
        
        try {
            // Convert Norwegian format to standard format
            // 123.456,78 -> 123456.78
            String standardFormat = norwegianAmount
                .replace(".", "")  // Remove thousand separators
                .replace(",", "."); // Convert decimal comma to dot
            
            return Double.parseDouble(standardFormat.trim());
        } catch (NumberFormatException e) {
            logger.warn("Failed to parse Norwegian currency {}: {}", norwegianAmount, e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Parse complex JSON response from Bank API and validate account ownership
     * 
     * COMPLEX JSON STRUCTURE with mixed naming conventions:
     * - account-number (kebab-case)
     * - account_holder.national_id (snake_case)
     * - accountStatus.status_code (mixed case)
     * - metadata.request_timestamp (snake_case)
     * 
     * INTEGRATION COMPLEXITY:
     * - Navigate nested JSON structure
     * - Handle mixed naming conventions
     * - Extract relevant validation fields
     * - Compare national ID for ownership validation
     * - Handle missing or null fields gracefully
     */
    private boolean parseComplexJsonBankResponse(String jsonResponse, String expectedAccountNumber, String expectedFnr) {
        try {
            logger.debug("Parsing complex JSON bank response");
            
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            
            // Navigate complex nested structure to extract account information
            
            // Extract account number with kebab-case
            JsonNode accountNumberNode = rootNode.get("account-number");
            String accountNumber = accountNumberNode != null ? accountNumberNode.asText() : null;
            
            // Extract account holder information with snake_case
            JsonNode accountHolderNode = rootNode.get("account_holder");
            if (accountHolderNode == null) {
                logger.warn("Missing account_holder in bank response");
                return false;
            }
            
            JsonNode nationalIdNode = accountHolderNode.get("national_id");
            String nationalId = nationalIdNode != null ? nationalIdNode.asText() : null;
            
            JsonNode fullNameNode = accountHolderNode.get("full_name");
            String fullName = fullNameNode != null ? fullNameNode.asText() : null;
            
            // Extract account status with mixed naming conventions
            JsonNode accountStatusNode = rootNode.get("account-status");
            String statusCode = "UNKNOWN";
            boolean isVerified = false;
            
            if (accountStatusNode != null) {
                JsonNode statusCodeNode = accountStatusNode.get("status_code");
                statusCode = statusCodeNode != null ? statusCodeNode.asText() : "UNKNOWN";
                
                JsonNode isVerifiedNode = accountStatusNode.get("is_verified");
                isVerified = isVerifiedNode != null && isVerifiedNode.asBoolean();
            }
            
            // Validate account ownership and status
            boolean accountNumberMatches = expectedAccountNumber.equals(accountNumber);
            boolean nationalIdMatches = expectedFnr.equals(nationalId);
            boolean accountActive = "ACTIVE".equalsIgnoreCase(statusCode);
            
            logger.info("Bank validation: accountMatch={}, nationalIdMatch={}, active={}, verified={}, name={}", 
                       accountNumberMatches, nationalIdMatches, accountActive, isVerified, fullName);
            
            // Account is valid if all conditions are met
            return accountNumberMatches && nationalIdMatches && accountActive && isVerified;
            
        } catch (Exception e) {
            logger.error("Failed to parse complex JSON response from Bank API: {}", e.getMessage());
            return false; // Return false for security on parse errors
        }
    }

    // Token-henting (simulert OAuth 2.0 flow)

    private String hentFolkeregisterToken() {
        // I praksis: OAuth 2.0 client credentials flow mot Folkeregister
        logger.debug("Henter Folkeregister access token");
        return "FOLKEREGISTER_TOKEN_" + System.currentTimeMillis();
    }

    private String hentAOrdningenToken() {
        logger.debug("Henter A-ordningen access token");
        return "A_ORDNINGEN_TOKEN_" + System.currentTimeMillis();
    }

    private String hentBankValideringToken() {
        logger.debug("Henter bank validering access token");
        return "BANK_TOKEN_" + System.currentTimeMillis();
    }

    private String hentSkatteetatenToken() {
        logger.debug("Henter Skatteetaten access token");
        return "SKATTEETATEN_TOKEN_" + System.currentTimeMillis();
    }
}

// Custom Exceptions - kunne vært i egen exception pakke
class EksternIntegrasjonException extends RuntimeException {
    public EksternIntegrasjonException(String message) {
        super(message);
    }
    
    public EksternIntegrasjonException(String message, Throwable cause) {
        super(message, cause);
    }
}

class PersonIkkeFunnetException extends EksternIntegrasjonException {
    public PersonIkkeFunnetException(String message) {
        super(message);
    }
}

class TilgangNektetException extends EksternIntegrasjonException {
    public TilgangNektetException(String message) {
        super(message);
    }
}
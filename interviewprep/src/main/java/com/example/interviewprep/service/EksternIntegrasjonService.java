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

    // Simulerte base URLs for eksterne systemer
    private static final String FOLKEREGISTER_BASE_URL = "https://api.folkeregister.no";
    private static final String A_ORDNINGEN_BASE_URL = "https://api.a-ordningen.no";
    private static final String BANK_VALIDERING_BASE_URL = "https://api.bankvalidering.no";
    private static final String SKATTEETATEN_BASE_URL = "https://api.skatteetaten.no";

    @Autowired
    public EksternIntegrasjonService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Hent person-informasjon fra Folkeregister
     * 
     * KRITISK INTEGRASJON: All NAV-saksbehandling avhenger av korrekt person-data
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
        logger.info("Henter persondata fra Folkeregister for person");

        try {
            // 1. SIKKERHET - Bygg request med authentication
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(hentFolkeregisterToken()); // OAuth 2.0 token
            headers.set("X-Request-ID", UUID.randomUUID().toString()); // Sporing
            headers.set("X-Consumer-ID", "NAV-INTEGRATION-DEMO"); // Identifikasjon

            HttpEntity<String> request = new HttpEntity<>(headers);

            // 2. API KALL - GET til Folkeregister
            String url = FOLKEREGISTER_BASE_URL + "/api/v1/person/" + fnr;
            
            logger.debug("Kaller Folkeregister API: {}", url);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                request, 
                String.class
            );

            // 3. RESPONS PROSESSERING
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                
                FolkeregisterData personData = new FolkeregisterData(
                    jsonResponse.get("fodselsnummer").asText(),
                    jsonResponse.get("navn").asText(),
                    jsonResponse.get("adresse").asText(),
                    jsonResponse.get("sivilstand").asText(),
                    jsonResponse.get("doedsfall").asBoolean(false)
                );

                logger.info("Hentet persondata fra Folkeregister OK");
                return personData;
            } else {
                logger.warn("Uventet status fra Folkeregister: {}", response.getStatusCode());
                throw new EksternIntegrasjonException("Folkeregister returnte status: " + response.getStatusCode());
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
     * Hent arbeidsforhold fra A-ordningen
     * 
     * KRITISK FOR DAGPENGER: Arbeidshistorikk bestemmer rettigheter
     */
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public List<ArbeidsforholdData> hentArbeidsforholdFraAOrdningen(String fnr, LocalDateTime fradato) {
        logger.info("Henter arbeidsforhold fra A-ordningen for person siden {}", fradato);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(hentAOrdningenToken());
            headers.set("X-Request-ID", UUID.randomUUID().toString());
            headers.set("X-Consumer-ID", "NAV-INTEGRATION-DEMO");

            // Query parameters for dato-filter
            String url = A_ORDNINGEN_BASE_URL + "/api/v1/arbeidsforhold/" + fnr 
                        + "?fraOgMed=" + fradato.toString();

            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, request, String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                List<ArbeidsforholdData> arbeidsforhold = new ArrayList<>();

                // Parse JSON array
                if (jsonResponse.isArray()) {
                    for (JsonNode arbeidsforholdNode : jsonResponse) {
                        arbeidsforhold.add(new ArbeidsforholdData(
                            arbeidsforholdNode.get("orgnummer").asText(),
                            arbeidsforholdNode.get("startdato").asText(),
                            arbeidsforholdNode.get("sluttdato").asText(null),
                            arbeidsforholdNode.get("stilling").asText(),
                            arbeidsforholdNode.get("prosent").asDouble()
                        ));
                    }
                }

                logger.info("Hentet {} arbeidsforhold fra A-ordningen", arbeidsforhold.size());
                return arbeidsforhold;
            }

            throw new EksternIntegrasjonException("A-ordningen returnte status: " + response.getStatusCode());

        } catch (Exception e) {
            logger.error("Feil ved henting av arbeidsforhold: {}", e.getMessage());
            throw new EksternIntegrasjonException("A-ordningen integrasjon feilet", e);
        }
    }

    /**
     * Valider bankkonto for utbetaling
     * 
     * KRITISK FOR UTBETALINGER: Må sikre at penger går til rett konto
     */
    public boolean validerBankkonto(String kontonummer, String fnr) {
        logger.info("Validerer bankkonto for utbetaling");

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(hentBankValideringToken());

            // Request body med konto og eier-info
            Map<String, String> requestBody = Map.of(
                "kontonummer", kontonummer,
                "eierFnr", fnr
            );

            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);
            
            String url = BANK_VALIDERING_BASE_URL + "/api/v1/valider-konto";
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, request, String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                boolean gyldig = jsonResponse.get("gyldig").asBoolean();
                
                logger.info("Bankkonto validering resultat: {}", gyldig ? "GYLDIG" : "UGYLDIG");
                return gyldig;
            }

            return false;

        } catch (Exception e) {
            logger.error("Feil ved bankonto validering: {}", e.getMessage());
            // Ved feil, returner false for sikkerhet
            return false;
        }
    }

    /**
     * Hent inntektsdata fra Skatteetaten
     * 
     * KRITISK FOR YTELSESBEREGNING: Inntekt bestemmer utbetalingsbeløp
     */
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public InntektsData hentInntektFraSkatteetaten(String fnr, int aar) {
        logger.info("Henter inntektsdata fra Skatteetaten for år {}", aar);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(hentSkatteetatenToken());
            headers.set("X-Request-ID", UUID.randomUUID().toString());

            String url = SKATTEETATEN_BASE_URL + "/api/v1/inntekt/" + fnr + "/" + aar;
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, request, String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                
                InntektsData inntekt = new InntektsData(
                    fnr,
                    aar,
                    jsonResponse.get("bruttoInntekt").asDouble(),
                    jsonResponse.get("skattepliktigInntekt").asDouble(),
                    jsonResponse.get("pensjonsinntekt").asDouble(0.0)
                );

                logger.info("Hentet inntektsdata fra Skatteetaten OK");
                return inntekt;
            }

            throw new EksternIntegrasjonException("Skatteetaten returnte status: " + response.getStatusCode());

        } catch (Exception e) {
            logger.error("Feil ved henting av inntektsdata: {}", e.getMessage());
            throw new EksternIntegrasjonException("Skatteetaten integrasjon feilet", e);
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
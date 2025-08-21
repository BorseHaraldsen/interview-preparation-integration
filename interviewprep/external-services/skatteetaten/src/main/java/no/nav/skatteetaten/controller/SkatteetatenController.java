package no.nav.skatteetaten.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Skatteetaten Controller - Returns fixed-width format data
 * 
 * This controller demonstrates legacy mainframe integration challenges
 * by returning data in fixed-width format that must be parsed field by field.
 * 
 * Fixed-width record format (total 200 characters per line):
 * Pos 1-11:   Fødselsnummer (11 chars)
 * Pos 12-13:  Filler spaces (2 chars) 
 * Pos 14-28:  Inntekt hovedjobb (15 chars, right-aligned, Norwegian decimal)
 * Pos 29-43:  Inntekt bijobb (15 chars, right-aligned, Norwegian decimal)
 * Pos 44-58:  Skatt betalt (15 chars, right-aligned, Norwegian decimal)
 * Pos 59-68:  Skatteår (10 chars, format YYYY)
 * Pos 69-78:  Status (10 chars, left-aligned)
 * Pos 79-88:  Siste endring (10 chars, dd.MM.yyyy)
 * Pos 89-200: Filler spaces (112 chars)
 */
@RestController  
@RequestMapping("/api/skatt")
public class SkatteetatenController {
    
    private static final Pattern FØDSELSNUMMER_PATTERN = Pattern.compile("\\d{11}");
    private static final DateTimeFormatter NORWEGIAN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    
    // Norwegian number formatting (comma as decimal separator)
    private static final DecimalFormat NORWEGIAN_CURRENCY;
    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("no", "NO"));
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator('.');
        NORWEGIAN_CURRENCY = new DecimalFormat("###,##0.00", symbols);
    }

    /**
     * Hent skattedata i fixed-width format
     * Integration layer må parse hver posisjon for å ekstraktere data
     */
    @GetMapping(value = "/person/{fødselsnummer}/inntekt/{år}", 
                produces = MediaType.TEXT_PLAIN_VALUE)
    public String hentSkattedata(@PathVariable String fødselsnummer, 
                                @PathVariable String år) throws InterruptedException {
        
        // Simulate mainframe processing delay (realistic for legacy systems)
        Thread.sleep(500 + (long)(Math.random() * 1000)); // 500-1500ms delay
        
        if (!FØDSELSNUMMER_PATTERN.matcher(fødselsnummer).matches()) {
            return buildErrorRecord("UGYLDIG_FNR", fødselsnummer);
        }
        
        if (!isValidYear(år)) {
            return buildErrorRecord("UGYLDIG_ÅR", fødselsnummer);
        }
        
        return buildSkatteRecord(fødselsnummer, år);
    }

    /**
     * Health check i fixed-width format (ikke standard JSON)
     */
    @GetMapping(value = "/helse", produces = MediaType.TEXT_PLAIN_VALUE)
    public String healthCheck() {
        StringBuilder sb = new StringBuilder();
        sb.append("STATUS    ").append("OK        ")
          .append("TJENESTE  ").append("SKATTEETATEN")
          .append("TID       ").append(LocalDate.now().format(NORWEGIAN_DATE))
          .append(" ".repeat(200 - sb.length())); // Pad to 200 chars
        return sb.toString();
    }

    /**
     * Build fixed-width tax record - forces integration to handle field parsing
     */
    private String buildSkatteRecord(String fødselsnummer, String år) {
        
        // Generate realistic tax data based on fødselsnummer
        double inntektHovedjobb = generateInntekt(fødselsnummer, "hovedjobb");
        double inntektBijobb = generateInntekt(fødselsnummer, "bijobb"); 
        double skattBetalt = inntektHovedjobb * 0.28 + inntektBijobb * 0.35; // Norwegian tax rates
        String status = generateStatus(fødselsnummer);
        String sisteEndring = LocalDate.now().format(NORWEGIAN_DATE);
        
        // Build fixed-width record (each field has exact position and width)
        StringBuilder record = new StringBuilder();
        
        // Pos 1-11: Fødselsnummer (11 chars)
        record.append(fødselsnummer);
        
        // Pos 12-13: Filler (2 chars)
        record.append("  ");
        
        // Pos 14-28: Inntekt hovedjobb (15 chars, right-aligned)
        record.append(formatCurrencyField(inntektHovedjobb, 15));
        
        // Pos 29-43: Inntekt bijobb (15 chars, right-aligned)
        record.append(formatCurrencyField(inntektBijobb, 15));
        
        // Pos 44-58: Skatt betalt (15 chars, right-aligned)
        record.append(formatCurrencyField(skattBetalt, 15));
        
        // Pos 59-68: Skatteår (10 chars)
        record.append(String.format("%-10s", år));
        
        // Pos 69-78: Status (10 chars, left-aligned)
        record.append(String.format("%-10s", status));
        
        // Pos 79-88: Siste endring (10 chars)
        record.append(sisteEndring);
        
        // Pos 89-200: Filler spaces (112 chars)
        record.append(" ".repeat(200 - record.length()));
        
        return record.toString();
    }
    
    private String buildErrorRecord(String errorCode, String fødselsnummer) {
        StringBuilder record = new StringBuilder();
        record.append(fødselsnummer);
        record.append("  "); // Filler
        record.append("ERROR:").append(String.format("%-9s", errorCode));
        record.append(" ".repeat(200 - record.length()));
        return record.toString();
    }
    
    /**
     * Format currency in Norwegian format for fixed-width field
     * Right-aligned with space padding
     */
    private String formatCurrencyField(double amount, int width) {
        String formatted = NORWEGIAN_CURRENCY.format(amount);
        return String.format("%" + width + "s", formatted);
    }
    
    private double generateInntekt(String fødselsnummer, String type) {
        // Generate realistic income based on fødselsnummer
        int seed = Integer.parseInt(fødselsnummer.substring(0, 3));
        double baseIncome = type.equals("hovedjobb") ? 450000 : 50000;
        return baseIncome + (seed * 1000) + (Math.random() * 100000);
    }
    
    private String generateStatus(String fødselsnummer) {
        String[] statuses = {"AKTIV", "INAKTIV", "BEHANDLING", "AVSLUTTET"};
        int statusIdx = Integer.parseInt(fødselsnummer.substring(0, 1)) % statuses.length;
        return statuses[statusIdx];
    }
    
    private boolean isValidYear(String år) {
        try {
            int year = Integer.parseInt(år);
            return year >= 2020 && year <= LocalDate.now().getYear();
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
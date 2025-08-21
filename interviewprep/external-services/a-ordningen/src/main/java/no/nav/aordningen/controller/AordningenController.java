package no.nav.aordningen.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A-Ordningen Controller - Returns CSV data with pipe delimiters
 * 
 * This controller demonstrates employment data integration challenges
 * by returning data in CSV format with Norwegian headers and pipe delimiters.
 * 
 * CSV format with pipe delimiters (header + data rows):
 * fødselsnummer|arbeidsgiver_orgnr|arbeidsforhold_id|stillingsprosent|månedlønn|framdato|tildato|stillingstype
 * 
 * Each employment record requires parsing and field mapping to internal structures.
 */
@RestController
@RequestMapping("/api/aordningen")
public class AordningenController {
    
    private static final Pattern FØDSELSNUMMER_PATTERN = Pattern.compile("\\d{11}");
    private static final Pattern ORGNR_PATTERN = Pattern.compile("\\d{9}");
    private static final DateTimeFormatter NORWEGIAN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String CSV_DELIMITER = "|"; // Pipe delimiter instead of comma

    /**
     * Hent arbeidsforhold i CSV format med pipe-delimitere
     * Integration layer må parse CSV og mappe felt til JSON struktur
     */
    @GetMapping(value = "/arbeidsforhold/{fødselsnummer}", 
                produces = "text/csv; charset=UTF-8")
    public ResponseEntity<String> hentArbeidsforhold(
            @PathVariable String fødselsnummer,
            @RequestParam(name = "framdato", required = false) String framdato,
            @RequestParam(name = "tildato", required = false) String tildato) throws InterruptedException {
        
        // Simulate batch processing delay (realistic for employment data systems)
        Thread.sleep(400 + (long)(Math.random() * 600)); // 400-1000ms delay
        
        if (!FØDSELSNUMMER_PATTERN.matcher(fødselsnummer).matches()) {
            return ResponseEntity.badRequest()
                .body("ERROR|UGYLDIG_FØDSELSNUMMER|" + fødselsnummer + "|\n");
        }
        
        String csvData = buildArbeidsforholdCsv(fødselsnummer, framdato, tildato);
        
        // Set proper CSV headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.set("Content-Disposition", "attachment; filename=arbeidsforhold_" + fødselsnummer + ".csv");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(csvData);
    }

    /**
     * Health check i CSV format (ikke standard JSON)  
     */
    @GetMapping(value = "/helse", produces = "text/csv; charset=UTF-8")
    public String healthCheck() {
        StringBuilder csv = new StringBuilder();
        csv.append("tjeneste").append(CSV_DELIMITER)
           .append("status").append(CSV_DELIMITER) 
           .append("tidspunkt").append("\n");
        
        csv.append("A-ORDNINGEN").append(CSV_DELIMITER)
           .append("OK").append(CSV_DELIMITER)
           .append(LocalDate.now().format(NORWEGIAN_DATE)).append("\n");
        
        return csv.toString();
    }

    /**
     * Build CSV arbeidsforhold data with Norwegian headers and pipe delimiters
     * Forces integration to handle CSV parsing and field mapping
     */
    private String buildArbeidsforholdCsv(String fødselsnummer, String framdato, String tildato) {
        StringBuilder csv = new StringBuilder();
        
        // Norwegian CSV header with pipe delimiters
        csv.append("fødselsnummer").append(CSV_DELIMITER)
           .append("arbeidsgiver_orgnr").append(CSV_DELIMITER)
           .append("arbeidsforhold_id").append(CSV_DELIMITER)
           .append("stillingsprosent").append(CSV_DELIMITER)
           .append("månedlønn").append(CSV_DELIMITER)
           .append("framdato").append(CSV_DELIMITER)
           .append("tildato").append(CSV_DELIMITER)
           .append("stillingstype").append(CSV_DELIMITER)
           .append("arbeidssted_kommune").append(CSV_DELIMITER)
           .append("yrke_kode").append(CSV_DELIMITER)
           .append("status").append("\n");
        
        // Generate multiple employment records for realistic data
        List<ArbeidsforholdRecord> arbeidsforhold = generateArbeidsforhold(fødselsnummer);
        
        for (ArbeidsforholdRecord record : arbeidsforhold) {
            csv.append(record.toCsvLine()).append("\n");
        }
        
        return csv.toString();
    }
    
    /**
     * Generate realistic employment records based on fødselsnummer
     */
    private List<ArbeidsforholdRecord> generateArbeidsforhold(String fødselsnummer) {
        List<ArbeidsforholdRecord> arbeidsforhold = new ArrayList<>();
        
        // Generate 1-3 employment records based on fødselsnummer
        int numRecords = 1 + (Integer.parseInt(fødselsnummer.substring(0, 1)) % 3);
        
        String[] arbeidsgiverOrgnr = {"123456789", "987654321", "555666777", "111222333", "999888777"};
        String[] stillingstyper = {"FAST", "VIKARIAT", "SESONG", "LÆRLINGE", "DELTID"};
        String[] kommuner = {"0301", "1103", "5001", "3005", "4601"}; // Oslo, Stavanger, Bergen, Drammen, Kristiansand
        String[] yrkekoder = {"2310", "5120", "7110", "4110", "8110"}; // Various occupation codes
        
        for (int i = 0; i < numRecords; i++) {
            int orgIndex = (Integer.parseInt(fødselsnummer.substring(i + 1, i + 2)) % arbeidsgiverOrgnr.length);
            int stillingIndex = (Integer.parseInt(fødselsnummer.substring(i + 2, i + 3)) % stillingstyper.length);
            
            LocalDate framdato = LocalDate.now().minusYears(2 + i).plusMonths(i * 6);
            LocalDate tildato = (i == 0) ? null : framdato.plusYears(1); // Current job has no end date
            
            ArbeidsforholdRecord record = new ArbeidsforholdRecord(
                fødselsnummer,
                arbeidsgiverOrgnr[orgIndex],
                "ARB_" + fødselsnummer.substring(0, 6) + "_" + i,
                80.0 + (i * 10), // 80%, 90%, 100% employment
                35000 + (i * 5000), // Increasing salary
                framdato,
                tildato,
                stillingstyper[stillingIndex],
                kommuner[i % kommuner.length],
                yrkekoder[i % yrkekoder.length],
                "AKTIV"
            );
            
            arbeidsforhold.add(record);
        }
        
        return arbeidsforhold;
    }
    
    /**
     * Employment record structure for CSV generation
     */
    private static class ArbeidsforholdRecord {
        private final String fødselsnummer;
        private final String arbeidsgiverOrgnr;
        private final String arbeidsforholdId;
        private final double stillingsprosent;
        private final int månedlønn;
        private final LocalDate framdato;
        private final LocalDate tildato; // null if current employment
        private final String stillingstype;
        private final String arbeidsstedKommune;
        private final String yrkeKode;
        private final String status;
        
        public ArbeidsforholdRecord(String fødselsnummer, String arbeidsgiverOrgnr, String arbeidsforholdId,
                                  double stillingsprosent, int månedlønn, LocalDate framdato, LocalDate tildato,
                                  String stillingstype, String arbeidsstedKommune, String yrkeKode, String status) {
            this.fødselsnummer = fødselsnummer;
            this.arbeidsgiverOrgnr = arbeidsgiverOrgnr;
            this.arbeidsforholdId = arbeidsforholdId;
            this.stillingsprosent = stillingsprosent;
            this.månedlønn = månedlønn;
            this.framdato = framdato;
            this.tildato = tildato;
            this.stillingstype = stillingstype;
            this.arbeidsstedKommune = arbeidsstedKommune;
            this.yrkeKode = yrkeKode;
            this.status = status;
        }
        
        /**
         * Convert to CSV line with pipe delimiters
         * Handles null values as empty fields (realistic CSV challenge)
         */
        public String toCsvLine() {
            StringBuilder line = new StringBuilder();
            line.append(fødselsnummer).append(CSV_DELIMITER)
                .append(arbeidsgiverOrgnr).append(CSV_DELIMITER)
                .append(arbeidsforholdId).append(CSV_DELIMITER)
                .append(formatStillingsprosent()).append(CSV_DELIMITER)
                .append(månedlønn).append(CSV_DELIMITER)
                .append(framdato.format(NORWEGIAN_DATE)).append(CSV_DELIMITER)
                .append(tildato != null ? tildato.format(NORWEGIAN_DATE) : "").append(CSV_DELIMITER) // Empty for null
                .append(stillingstype).append(CSV_DELIMITER)
                .append(arbeidsstedKommune).append(CSV_DELIMITER)
                .append(yrkeKode).append(CSV_DELIMITER)
                .append(status);
            
            return line.toString();
        }
        
        private String formatStillingsprosent() {
            // Norwegian decimal format with comma (creates locale parsing challenges)
            return String.format("%.1f", stillingsprosent).replace('.', ',');
        }
    }
}
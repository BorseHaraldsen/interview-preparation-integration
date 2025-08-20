package com.example.interviewprep.models.hendelser;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import com.example.interviewprep.models.SaksType;


/**
 * Hendelse for integrasjon med eksterne systemer
 * 
 * Brukes når NAV-systemet må kommunisere med:
 * - Folkeregisteret (hent persondata)
 * - Banksystemer (utbetalinger)  
 * - A-ordningen (lønnsdata)
 * - Skatteetaten (skattdata)
 * - Syfo (sykefraværsdata)
 * 
 * Plassering: src/main/java/no/nav/integration/model/hendelser/EksternIntegrasjonHendelse.java
 */
public class EksternIntegrasjonHendelse extends BaseHendelse {
    
    @JsonProperty("sakId")
    private Long sakId;
    
    @JsonProperty("eksternSystem")
    private String eksternSystem;  // FOLKEREGISTER, UTBETALING, A_ORDNINGEN, etc.
    
    @JsonProperty("operasjon")
    private String operasjon;      // HENT_BRUKERINFO, OPPRETT_UTBETALING, etc.
    
    @JsonProperty("forespørselData")
    private Map<String, Object> forespørselData;
    
    @JsonProperty("prioritet")
    private String prioritet;      // HØY, NORMAL, LAV

    /**
     * Default konstruktør for JSON deserialisering
     */
    public EksternIntegrasjonHendelse() {
        super("EKSTERN_INTEGRASJON");
    }

    /**
     * Konstruktør for å opprette ekstern integrasjonshendelse
     * 
     * @param sakId ID på sak som trigger integrasjonen
     * @param eksternSystem Hvilket eksternt system som skal kontaktes
     * @param operasjon Hvilken operasjon som skal utføres
     * @param forespørselData Data som trengs for operasjonen
     * @param prioritet Prioritet på forespørselen
     */
    public EksternIntegrasjonHendelse(Long sakId, String eksternSystem, String operasjon, 
                                     Map<String, Object> forespørselData, String prioritet) {
        this();
        this.sakId = sakId;
        this.eksternSystem = eksternSystem;
        this.operasjon = operasjon;
        this.forespørselData = forespørselData;
        this.prioritet = prioritet;
    }

    /**
     * Hjelpemetode for å opprette folkeregister-forespørsel
     */
    public static EksternIntegrasjonHendelse opprettFolkeregisterForespørsel(Long sakId, String fnr) {
        Map<String, Object> data = Map.of(
                "fodselsnummer", maskertFnr(fnr),
                "operasjonType", "HENT_PERSONINFO",
                "dataelementer", "navn,adresse,sivilstatus,barn"
        );
        
        return new EksternIntegrasjonHendelse(
                sakId, 
                "FOLKEREGISTER", 
                "HENT_BRUKERINFO", 
                data, 
                "NORMAL"
        );
    }

    /**
     * Hjelpemetode for å opprette utbetalings-forespørsel
     */
    public static EksternIntegrasjonHendelse opprettUtbetalingsForespørsel(
            Long sakId, String brukerFnr, SaksType saksType, double beløp) {
        
        Map<String, Object> data = Map.of(
                "mottakerFnr", maskertFnr(brukerFnr),
                "beløp", beløp,
                "utbetalingType", saksType.name(),
                "beskrivelse", "Utbetaling " + saksType.getBeskrivelse()
        );
        
        return new EksternIntegrasjonHendelse(
                sakId, 
                "UTBETALING", 
                "OPPRETT_UTBETALING", 
                data, 
                "HØY"  // Utbetalinger har høy prioritet
        );
    }

    /**
     * Masker fødselsnummer i integrasjonsdata
     */
    private static String maskertFnr(String fnr) {
        if (fnr == null || fnr.length() < 6) {
            return "***";
        }
        return fnr.substring(0, 6) + "*****";
    }

    // Getters og setters
    public Long getSakId() { return sakId; }
    public void setSakId(Long sakId) { this.sakId = sakId; }
    
    public String getEksternSystem() { return eksternSystem; }
    public void setEksternSystem(String eksternSystem) { this.eksternSystem = eksternSystem; }
    
    public String getOperasjon() { return operasjon; }
    public void setOperasjon(String operasjon) { this.operasjon = operasjon; }
    
    public Map<String, Object> getForespørselData() { return forespørselData; }
    public void setForespørselData(Map<String, Object> forespørselData) { this.forespørselData = forespørselData; }
    
    public String getPrioritet() { return prioritet; }
    public void setPrioritet(String prioritet) { this.prioritet = prioritet; }

    @Override
    public String toString() {
        return String.format("EksternIntegrasjonHendelse[sakId=%s, system=%s, operasjon=%s, prioritet=%s]", 
                           sakId, eksternSystem, operasjon, prioritet);
    }
}
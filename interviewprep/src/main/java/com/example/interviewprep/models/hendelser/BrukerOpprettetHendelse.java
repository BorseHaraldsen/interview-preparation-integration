package com.example.interviewprep.models.hendelser;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Hendelse når en bruker opprettes i systemet
 * 
 * Andre systemer kan lytte til dette for å holde sine data synkronisert
 * Eksempel: CRM-system, statistikk-system, rapportering
 * 
 * Plassering: src/main/java/no/nav/integration/model/hendelser/BrukerOpprettetHendelse.java
 */
public class BrukerOpprettetHendelse extends BaseHendelse {
    
    @JsonProperty("brukerId")
    private Long brukerId;
    
    @JsonProperty("fodselsnummer")
    private String fodselsnummer;  // Vil bli maskert
    
    @JsonProperty("navn")
    private String navn;
    
    @JsonProperty("adresse")
    private String adresse;

    /**
     * Default konstruktør for JSON deserialisering
     */
    public BrukerOpprettetHendelse() {
        super("BRUKER_OPPRETTET");
    }

    /**
     * Konstruktør for å opprette hendelse
     * 
     * @param brukerId ID på bruker som ble opprettet
     * @param fodselsnummer Brukerens fødselsnummer (maskeres automatisk)
     * @param navn Brukerens navn
     * @param adresse Brukerens adresse
     */
    public BrukerOpprettetHendelse(Long brukerId, String fodselsnummer, String navn, String adresse) {
        this();
        this.brukerId = brukerId;
        this.fodselsnummer = maskertFnr(fodselsnummer);  // Personvern!
        this.navn = navn;
        this.adresse = adresse;
    }

    /**
     * Masker fødselsnummer for personvern
     * Viktig i NAV-systemer hvor persondata må beskyttes
     */
    private String maskertFnr(String fnr) {
        if (fnr == null || fnr.length() < 6) {
            return "***";
        }
        return fnr.substring(0, 6) + "*****";
    }

    // Getters og setters
    public Long getBrukerId() { return brukerId; }
    public void setBrukerId(Long brukerId) { this.brukerId = brukerId; }
    
    public String getFodselsnummer() { return fodselsnummer; }
    public void setFodselsnummer(String fodselsnummer) { this.fodselsnummer = fodselsnummer; }
    
    public String getNavn() { return navn; }
    public void setNavn(String navn) { this.navn = navn; }
    
    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }

    @Override
    public String toString() {
        return String.format("BrukerOpprettetHendelse[brukerId=%s, navn=%s]", brukerId, navn);
    }
}
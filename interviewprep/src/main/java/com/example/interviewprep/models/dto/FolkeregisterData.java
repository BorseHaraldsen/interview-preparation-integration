package com.example.interviewprep.models.dto;

import com.example.interviewprep.models.Bruker;

/**
 * Data Transfer Object for Folkeregister API respons
 * 
 * HVORFOR EGEN DTO vs BRUKER MODELL?
 * 
 * 1. SEPARASJON AV BEKYMRINGER:
 *    - Folkeregister API kan endre format uten å påvirke vår database
 *    - Vår Bruker-modell har JPA annotations som ikke trengs her
 * 
 * 2. FORSKJELLIGE DATAFELTER:
 *    - Folkeregister: sivilstand, doedsfall (juridiske data)
 *    - Vår Bruker: opprettetTid, saker (forretningsdata)
 * 
 * 3. INTEGRASJONSMØNSTER - ANTI-CORRUPTION LAYER:
 *    - Dette DTO-et beskytter oss mot endringer i Folkeregister
 *    - Vi mapper explicit fra ekstern til intern modell
 * 
 * I INTERVJU kan du forklare:
 * "DTOs lar oss holde eksterne API-kontrakter separate fra vår 
 * interne datamodell. Dette gir fleksibilitet og robusthet."
 */
public class FolkeregisterData {
    
    private final String fnr;
    private final String navn;
    private final String adresse;
    private final String sivilstand;       // Finnes ikke i vår Bruker-modell
    private final boolean doedsfall;       // Finnes ikke i vår Bruker-modell

    public FolkeregisterData(String fnr, String navn, String adresse, 
                           String sivilstand, boolean doedsfall) {
        this.fnr = fnr;
        this.navn = navn;
        this.adresse = adresse;
        this.sivilstand = sivilstand;
        this.doedsfall = doedsfall;
    }

    // Getters
    public String getFnr() { return fnr; }
    public String getNavn() { return navn; }
    public String getAdresse() { return adresse; }
    public String getSivilstand() { return sivilstand; }
    public boolean isDoedsfall() { return doedsfall; }

    /**
     * Konverter til vår interne Bruker-modell
     * 
     * MAPPING PATTERN: Explicit transformation
     * Vi bestemmer selv hvilke felter som skal mappes hvordan
     */
    public com.example.interviewprep.models.Bruker tilBruker() {
        return new com.example.interviewprep.models.Bruker(fnr, navn, adresse);
        // Merk: sivilstand og doedsfall mappes IKKE - de er ikke relevante for vår forretningslogikk
    }

    @Override
    public String toString() {
        return "FolkeregisterData{" +
                "fnr='" + fnr.substring(0, 6) + "*****" + '\'' +
                ", navn='" + navn + '\'' +
                ", sivilstand='" + sivilstand + '\'' +
                ", doedsfall=" + doedsfall +
                '}';
    }
}
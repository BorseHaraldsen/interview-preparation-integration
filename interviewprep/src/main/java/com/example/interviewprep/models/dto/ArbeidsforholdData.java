package com.example.interviewprep.models.dto;

/**
 * DTO for A-ordningen arbeidsforhold data
 * 
 * HVORFOR EGEN DTO?
 * - A-ordningen har detaljerte arbeidsforhold-data
 * - Vi trenger kun deler av denne informasjonen
 * - Ekstern API kan endre uten å påvirke vår logikk
 */
public class ArbeidsforholdData {
    
    private final String orgnummer;
    private final String startdato;
    private final String sluttdato;    // Kan være null for pågående arbeidsforhold
    private final String stilling;
    private final double prosent;      // Stillingsprosent

    public ArbeidsforholdData(String orgnummer, String startdato, String sluttdato, 
                            String stilling, double prosent) {
        this.orgnummer = orgnummer;
        this.startdato = startdato;
        this.sluttdato = sluttdato;
        this.stilling = stilling;
        this.prosent = prosent;
    }

    // Getters
    public String getOrgnummer() { return orgnummer; }
    public String getStartdato() { return startdato; }
    public String getSluttdato() { return sluttdato; }
    public String getStilling() { return stilling; }
    public double getProsent() { return prosent; }

    /**
     * Sjekk om arbeidsforholdet er aktivt (ikke avsluttet)
     */
    public boolean erAktivt() {
        return sluttdato == null || sluttdato.isEmpty();
    }

    /**
     * Sjekk om det er heltidsstilling
     */
    public boolean erHeltid() {
        return prosent >= 100.0;
    }

    @Override
    public String toString() {
        return "ArbeidsforholdData{" +
                "orgnummer='" + orgnummer + '\'' +
                ", stilling='" + stilling + '\'' +
                ", prosent=" + prosent +
                ", aktivt=" + erAktivt() +
                '}';
    }
}
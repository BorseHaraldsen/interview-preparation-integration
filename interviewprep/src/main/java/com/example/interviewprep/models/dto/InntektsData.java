package com.example.interviewprep.models.dto;

/**
 * DTO for Skatteetaten inntektsdata
 * 
 * FORRETNINGSVERDI:
 * - Kritisk for beregning av dagpenger og andre ytelser
 * - Skatteetaten har detaljerte inntektstyper
 * - Vi trenger aggregerte tall for våre beregninger
 */
public class InntektsData {
    
    private final String fnr;
    private final int aar;
    private final double bruttoInntekt;
    private final double skattepliktigInntekt;
    private final double pensjonsinntekt;

    public InntektsData(String fnr, int aar, double bruttoInntekt, 
                       double skattepliktigInntekt, double pensjonsinntekt) {
        this.fnr = fnr;
        this.aar = aar;
        this.bruttoInntekt = bruttoInntekt;
        this.skattepliktigInntekt = skattepliktigInntekt;
        this.pensjonsinntekt = pensjonsinntekt;
    }

    // Getters
    public String getFnr() { return fnr; }
    public int getAar() { return aar; }
    public double getBruttoInntekt() { return bruttoInntekt; }
    public double getSkattepliktigInntekt() { return skattepliktigInntekt; }
    public double getPensjonsinntekt() { return pensjonsinntekt; }

    /**
     * Beregn relevant inntekt for dagpenger
     * (forenklet beregning for demo)
     */
    public double getDagpengerGrunnlag() {
        return skattepliktigInntekt; // Forenklet - i praksis mer kompleks
    }

    /**
     * Sjekk om inntekt kvalifiserer for dagpenger
     */
    public boolean kvalifisererForDagpenger() {
        // Forenklet regel: minst 150.000 kr årlig
        return skattepliktigInntekt >= 150000;
    }

    @Override
    public String toString() {
        return "InntektsData{" +
                "aar=" + aar +
                ", bruttoInntekt=" + bruttoInntekt +
                ", skattepliktigInntekt=" + skattepliktigInntekt +
                '}';
    }
}
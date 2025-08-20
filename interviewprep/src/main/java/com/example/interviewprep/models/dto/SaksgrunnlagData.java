package com.example.interviewprep.models.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Aggregert DTO som sammenstiller data fra multiple kilder
 * 
 * INTEGRASJONSMØNSTER: Aggregator Pattern
 * - Samler data fra Folkeregister, A-ordningen, Skatteetaten
 * - Gir komplett bilde for saksbehandling
 * - Cache-vennlig (alle data hentet samtidig)
 * 
 * I INTERVJU kan du forklare:
 * "Dette viser hvordan vi sammenstiller data fra flere kilder 
 * til ett komplett grunnlag for saksbehandling."
 */
public class SaksgrunnlagData {
    
    private final FolkeregisterData person;
    private final List<ArbeidsforholdData> arbeidsforhold;
    private final InntektsData inntekt;
    private final LocalDateTime hentetTidspunkt;

    public SaksgrunnlagData(FolkeregisterData person, 
                           List<ArbeidsforholdData> arbeidsforhold,
                           InntektsData inntekt, 
                           LocalDateTime hentetTidspunkt) {
        this.person = person;
        this.arbeidsforhold = arbeidsforhold;
        this.inntekt = inntekt;
        this.hentetTidspunkt = hentetTidspunkt;
    }

    // Getters
    public FolkeregisterData getPerson() { return person; }
    public List<ArbeidsforholdData> getArbeidsforhold() { return arbeidsforhold; }
    public InntektsData getInntekt() { return inntekt; }
    public LocalDateTime getHentetTidspunkt() { return hentetTidspunkt; }

    /**
     * Business logic: Vurder om dette kvalifiserer for dagpenger
     * 
     * FORRETNINGSLOGIKK I DTO:
     * - Kombinerer data fra flere kilder
     * - Gir anbefalinger basert på aggregerte data
     */
    public boolean kanFåDagpenger() {
        // Sjekk at person ikke er død
        if (person.isDoedsfall()) {
            return false;
        }

        // Sjekk inntektskrav
        if (inntekt == null || !inntekt.kvalifisererForDagpenger()) {
            return false;
        }

        // Sjekk at person ikke har aktivt arbeidsforhold
        boolean harAktivtArbeid = arbeidsforhold.stream()
                .anyMatch(ArbeidsforholdData::erAktivt);
        
        return !harAktivtArbeid;
    }

    /**
     * Få antall aktive arbeidsforhold
     */
    public int getAntallAktiveArbeidsforhold() {
        return (int) arbeidsforhold.stream()
                .filter(ArbeidsforholdData::erAktivt)
                .count();
    }

    /**
     * Sjekk om data er fersk (hentet i dag)
     */
    public boolean erDataFersk() {
        return hentetTidspunkt.isAfter(LocalDateTime.now().minusHours(24));
    }

    @Override
    public String toString() {
        return "SaksgrunnlagData{" +
                "person=" + person.getNavn() +
                ", arbeidsforhold=" + arbeidsforhold.size() + " stk" +
                ", harInntekt=" + (inntekt != null) +
                ", kanFåDagpenger=" + kanFåDagpenger() +
                '}';
    }
}
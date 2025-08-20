package com.example.interviewprep.models;


/**
 * Enum for sakstyper i NAV
 * Dette gjenspeiler faktiske NAV-tjenester
 
public enum SaksType {
    DAGPENGER("Dagpenger ved arbeidsledighet"),
    SYKEPENGER("Sykepenger"),
    AAP("Arbeidsavklaringspenger"),
    UFORETRYGD("Uføretrygd"),
    ALDERSPENSJON("Alderspensjon"),
    BARNETRYGD("Barnetrygd");

    private final String beskrivelse;

    SaksType(String beskrivelse) {
        this.beskrivelse = beskrivelse;
    }

    public String getBeskrivelse() { return beskrivelse; }
}


/**
 * Enum for sakstyper i NAV
 * 
 * Dette gjenspeiler faktiske NAV-tjenester og stønadstypene
 * Brukes i Sak entiteten for å sikre datakvalitet og konsistens
 * 
 * Plassering: src/main/java/no/nav/integration/model/SaksType.java
 */
public enum SaksType {
    
    /**
     * Dagpenger ved arbeidsledighet
     * For personer som har mistet jobben og oppfyller vilkårene
     */
    DAGPENGER("Dagpenger ved arbeidsledighet", "DP", 104),
    
    /**
     * Sykepenger 
     * Kompensasjon for tapt arbeidsinntekt ved sykdom
     */
    SYKEPENGER("Sykepenger", "SP", 26),
    
    /**
     * Arbeidsavklaringspenger (AAP)
     * Stønad til personer med nedsatt arbeidsevne
     */
    AAP("Arbeidsavklaringspenger", "AAP", 156),
    
    /**
     * Uføretrygd
     * Varig stønad ved varig nedsatt arbeidsevne
     */
    UFORETRYGD("Uføretrygd", "UFO", 260),
    
    /**
     * Alderspensjon
     * Pensjon fra NAV ved aldersgrensen
     */
    ALDERSPENSJON("Alderspensjon", "AP", 104),
    
    /**
     * Barnetrygd
     * Månedlig stønad til familier med barn
     */
    BARNETRYGD("Barnetrygd", "BA", 52);

    private final String beskrivelse;
    private final String kortKode;
    private final int maksVarighet; // uker

    /**
     * Konstruktør for SaksType
     * 
     * @param beskrivelse Menneskelig lesbar beskrivelse
     * @param kortKode Kort kode brukt i systemer (3 tegn)
     * @param maksVarighet Maksimal varighet i uker for denne sakstypen
     */
    SaksType(String beskrivelse, String kortKode, int maksVarighet) {
        this.beskrivelse = beskrivelse;
        this.kortKode = kortKode;
        this.maksVarighet = maksVarighet;
    }

    // Getters

    public String getBeskrivelse() {
        return beskrivelse;
    }

    public String getKortKode() {
        return kortKode;
    }

    public int getMaksVarighet() {
        return maksVarighet;
    }

    /**
     * Sjekk om sakstypen krever dokumentasjon
     * Forretningslogikk som bestemmer automatiseringsgrad
     */
    public boolean krevDokumentasjon() {
        return switch (this) {
            case BARNETRYGD, ALDERSPENSJON -> false; // Ofte automatisk behandlet
            case DAGPENGER -> false; // Kan automatiseres hvis enkelt
            case SYKEPENGER, AAP, UFORETRYGD -> true; // Krever medisinsk dokumentasjon
        };
    }

    /**
     * Bestem om sakstypen kan behandles automatisk
     * Viktig for AI/ML integrasjon som NAV jobber med
     */
    public boolean kanAutomatiskBehandles() {
        return switch (this) {
            case BARNETRYGD -> true; // Ofte standardisert
            case DAGPENGER -> true; // Hvis standard situasjon
            case ALDERSPENSJON -> true; // Ofte rett frem
            case SYKEPENGER, AAP, UFORETRYGD -> false; // Komplekse vurderinger
        };
    }

    /**
     * Hent forventet behandlingstid i virkedager
     * Brukes for SLA og oppfølging
     */
    public int getForventetBehandlingstidDager() {
        return switch (this) {
            case BARNETRYGD -> 5; // Rask
            case DAGPENGER -> 10; // Middels
            case SYKEPENGER -> 7; // Rask
            case ALDERSPENSJON -> 15; // Kan ta tid
            case AAP -> 30; // Kompleks vurdering
            case UFORETRYGD -> 45; // Meget kompleks
        };
    }

    /**
     * Bestem hvilke eksterne systemer som må kontaktes
     * Viktig for integrasjonsarkitektur
     */
    public java.util.List<String> getEksterneSystemer() {
        return switch (this) {
            case DAGPENGER -> java.util.List.of("A_ORDNINGEN", "FOLKEREGISTER", "ARENA");
            case SYKEPENGER -> java.util.List.of("FOLKEREGISTER", "SYFOAPI", "INFOTRYGD");
            case AAP -> java.util.List.of("FOLKEREGISTER", "ARENA", "GOSYS");
            case UFORETRYGD -> java.util.List.of("FOLKEREGISTER", "PESYS", "GOSYS");
            case ALDERSPENSJON -> java.util.List.of("FOLKEREGISTER", "PESYS", "POPP");
            case BARNETRYGD -> java.util.List.of("FOLKEREGISTER", "BA_SAK");
        };
    }

    /**
     * Finn sakstype basert på kort kode
     * Nyttig for integrasjon med legacy systemer
     */
    public static SaksType fraKortKode(String kortKode) {
        for (SaksType type : values()) {
            if (type.kortKode.equals(kortKode)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Ukjent sakstype kort kode: " + kortKode);
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", beskrivelse, kortKode);
    }
}
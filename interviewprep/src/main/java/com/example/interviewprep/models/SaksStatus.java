package com.example.interviewprep.models;

import java.util.List;
import java.util.Set;

/*
 *

 * Enum for sakstyper i NAV
 * Dette gjenspeiler faktiske NAV-tjenester
 


 * Enum for saksstatus
 * Viser saksflyt som er viktig i integrasjoner
 
public enum SaksStatus {
    MOTTATT("Søknad mottatt"),
    UNDER_BEHANDLING("Under behandling"),
    VENTER_DOKUMENTASJON("Venter på dokumentasjon"),
    VEDTAK_FATTET("Vedtak fattet"),
    UTBETALT("Utbetaling gjennomført"),
    AVSLUTTET("Sak avsluttet"),
    AVVIST("Søknad avvist");

    private final String beskrivelse;

    SaksStatus(String beskrivelse) {
        this.beskrivelse = beskrivelse;
    }

    public String getBeskrivelse() { return beskrivelse; }
}

/**
 * Enum for saksstatus i NAV
 * 
 * Dette definerer hele saksløpet fra mottak til avslutning
 * Viktig for saksflyt, integrasjoner og arbeidsflyt
 * 
 * Plassering: src/main/java/no/nav/integration/model/SaksStatus.java
 */
public enum SaksStatus {
    
    /**
     * Søknad er mottatt men ikke påbegynt behandling
     * Initial status for alle nye saker
     */
    MOTTATT("Søknad mottatt", "MO", 1, false, true),
    
    /**
     * Saken er under aktiv behandling av saksbehandler
     * Eller i automatisk behandlingsløp
     */
    UNDER_BEHANDLING("Under behandling", "UB", 2, true, true),
    
    /**
     * Venter på dokumentasjon fra bruker eller eksterne systemer
     * Saken er pause til dokumentasjon kommer
     */
    VENTER_DOKUMENTASJON("Venter på dokumentasjon", "VD", 3, true, true),
    
    /**
     * Vedtak er fattet (innvilget eller avslått)
     * Trigger utbetalinger og varsler
     */
    VEDTAK_FATTET("Vedtak fattet", "VF", 4, false, false),
    
    /**
     * Utbetaling er gjennomført for innvilgede saker
     * Indikerer at pengene er sendt til bruker
     */
    UTBETALT("Utbetaling gjennomført", "UT", 5, false, false),
    
    /**
     * Saken er helt avsluttet og arkiveres
     * Ingen flere handlinger påkrevd
     */
    AVSLUTTET("Sak avsluttet", "AV", 6, false, false),
    
    /**
     * Søknaden er avvist/avslått
     * Ingen utbetaling vil skje
     */
    AVVIST("Søknad avvist", "AW", 7, false, false);

    private final String beskrivelse;
    private final String kortKode;
    private final int sorteringsRekkefølge;
    private final boolean kreverOppfølging;
    private final boolean kanEndres;

    /**
     * Konstruktør for SaksStatus
     * 
     * @param beskrivelse Menneskelig lesbar beskrivelse
     * @param kortKode Kort kode brukt i integrasjoner (2 tegn)
     * @param sorteringsRekkefølge Naturlig rekkefølge i saksløpet
     * @param kreverOppfølging Om status indikerer behov for oppfølging
     * @param kanEndres Om status kan endres til andre statuser
     */
    SaksStatus(String beskrivelse, String kortKode, int sorteringsRekkefølge, 
               boolean kreverOppfølging, boolean kanEndres) {
        this.beskrivelse = beskrivelse;
        this.kortKode = kortKode;
        this.sorteringsRekkefølge = sorteringsRekkefølge;
        this.kreverOppfølging = kreverOppfølging;
        this.kanEndres = kanEndres;
    }

    // Getters

    public String getBeskrivelse() {
        return beskrivelse;
    }

    public String getKortKode() {
        return kortKode;
    }

    public int getSorteringsRekkefølge() {
        return sorteringsRekkefølge;
    }

    public boolean isKreverOppfølging() {
        return kreverOppfølging;
    }

    public boolean isKanEndres() {
        return kanEndres;
    }

    /**
     * Sjekk om status er aktiv (krever handlinger)
     * Brukes for arbeidslisteer og rapporter
     */
    public boolean isAktiv() {
        return Set.of(MOTTATT, UNDER_BEHANDLING, VENTER_DOKUMENTASJON).contains(this);
    }

    /**
     * Sjekk om status er ferdig (ingen flere handlinger)
     * Brukes for arkivering og statistikk
     */
    public boolean isFerdig() {
        return Set.of(VEDTAK_FATTET, UTBETALT, AVSLUTTET, AVVIST).contains(this);
    }

    /**
     * Sjekk om status indikerer suksess (positivt utfall)
     */
    public boolean isPositivtUtfall() {
        return Set.of(VEDTAK_FATTET, UTBETALT, AVSLUTTET).contains(this);
    }

    /**
     * Hent gyldige neste statuser fra denne statusen
     * Forretningslogikk for lovlige status-overganger
     */
    public List<SaksStatus> getGyldigeNesteStatuser() {
        return switch (this) {
            case MOTTATT -> List.of(UNDER_BEHANDLING, VENTER_DOKUMENTASJON, AVVIST);
            case UNDER_BEHANDLING -> List.of(VEDTAK_FATTET, VENTER_DOKUMENTASJON, AVVIST);
            case VENTER_DOKUMENTASJON -> List.of(UNDER_BEHANDLING, AVVIST);
            case VEDTAK_FATTET -> List.of(UTBETALT, AVSLUTTET);
            case UTBETALT -> List.of(AVSLUTTET);
            case AVSLUTTET, AVVIST -> List.of(); // Terminal statuser
        };
    }

    /**
     * Valider om overgang til ny status er gyldig
     * Viktig for integrasjoner som endrer saksstatus
     */
    public boolean kanGåTil(SaksStatus nyStatus) {
        return getGyldigeNesteStatuser().contains(nyStatus);
    }

    /**
     * Hent alle aktive statuser
     * Brukes ofte i database-spørringer
     */
    public static List<SaksStatus> getAktiveStatuser() {
        return List.of(MOTTATT, UNDER_BEHANDLING, VENTER_DOKUMENTASJON);
    }

    /**
     * Hent alle ferdige statuser
     * Brukes for rapporter og arkivering
     */
    public static List<SaksStatus> getFerdigeStatuser() {
        return List.of(VEDTAK_FATTET, UTBETALT, AVSLUTTET, AVVIST);
    }

    /**
     * Finn status basert på kort kode
     * Nyttig for integrasjon med legacy systemer som Oracle EBS
     */
    public static SaksStatus fraKortKode(String kortKode) {
        for (SaksStatus status : values()) {
            if (status.kortKode.equals(kortKode)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Ukjent saksstatus kort kode: " + kortKode);
    }

    /**
     * Hent status som krever oppfølging
     * Brukes for å identifisere saker som trenger human intervention
     */
    public static List<SaksStatus> getStatuserSomKreverOppfølging() {
        return List.of(UNDER_BEHANDLING, VENTER_DOKUMENTASJON);
    }

    /**
     * Sjekk om denne statusen kommer etter en annen i saksløpet
     * Brukes for å validere fremgang i saksbehandling
     */
    public boolean kommerEtter(SaksStatus annenStatus) {
        return this.sorteringsRekkefølge > annenStatus.sorteringsRekkefølge;
    }

    /**
     * Hent CSS-klasse for frontend styling
     * Brukes i webgrensesnitt for visuell feedback
     */
    public String getCssKlasse() {
        return switch (this) {
            case MOTTATT -> "status-new";
            case UNDER_BEHANDLING -> "status-active";
            case VENTER_DOKUMENTASJON -> "status-waiting";
            case VEDTAK_FATTET -> "status-decided";
            case UTBETALT -> "status-paid";
            case AVSLUTTET -> "status-completed";
            case AVVIST -> "status-rejected";
        };
    }

    /**
     * Hent emoji for visuell representasjon
     * Brukes i dashboards og notifikasjoner
     */
    public String getEmoji() {
        return switch (this) {
            case MOTTATT -> "📥";
            case UNDER_BEHANDLING -> "⚙️";
            case VENTER_DOKUMENTASJON -> "📄";
            case VEDTAK_FATTET -> "✅";
            case UTBETALT -> "💰";
            case AVSLUTTET -> "🏁";
            case AVVIST -> "❌";
        };
    }

    @Override
    public String toString() {
        return String.format("%s %s (%s)", getEmoji(), beskrivelse, kortKode);
    }
}
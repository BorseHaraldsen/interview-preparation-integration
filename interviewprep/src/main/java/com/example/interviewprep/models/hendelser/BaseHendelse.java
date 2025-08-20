package com.example.interviewprep.models.hendelser;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base klasse for alle hendelser i NAV-systemet
 * 
 * Dette følger Event Sourcing pattern hvor alle endringer logges som hendelser
 * Viktig for sporing, debugging og integrasjon mellom systemer
 * 
 * Plassering: src/main/java/no/nav/integration/model/hendelser/BaseHendelse.java
 */
public abstract class BaseHendelse {
    
    @JsonProperty("hendelseId")
    private String hendelseId;
    
    @JsonProperty("hendelseType")
    private String hendelseType;
    
    @JsonProperty("tidspunkt")
    private LocalDateTime tidspunkt;
    
    @JsonProperty("kilde")
    private String kilde;
    
    @JsonProperty("versjon")
    private String versjon;
    
    @JsonProperty("korrelasjonId")
    private String korrelasjonId;

    /**
     * Default konstruktør som setter felles felter
     */
    public BaseHendelse() {
        this.hendelseId = UUID.randomUUID().toString();
        this.tidspunkt = LocalDateTime.now();
        this.kilde = "nav-integration-demo";
        this.versjon = "1.0";
        this.korrelasjonId = UUID.randomUUID().toString();
    }

    /**
     * Konstruktør med hendelse type
     */
    public BaseHendelse(String hendelseType) {
        this();
        this.hendelseType = hendelseType;
    }

    // Getters og setters
    public String getHendelseId() { return hendelseId; }
    public void setHendelseId(String hendelseId) { this.hendelseId = hendelseId; }
    
    public String getHendelseType() { return hendelseType; }
    public void setHendelseType(String hendelseType) { this.hendelseType = hendelseType; }
    
    public LocalDateTime getTidspunkt() { return tidspunkt; }
    public void setTidspunkt(LocalDateTime tidspunkt) { this.tidspunkt = tidspunkt; }
    
    public String getKilde() { return kilde; }
    public void setKilde(String kilde) { this.kilde = kilde; }
    
    public String getVersjon() { return versjon; }
    public void setVersjon(String versjon) { this.versjon = versjon; }
    
    public String getKorrelasjonId() { return korrelasjonId; }
    public void setKorrelasjonId(String korrelasjonId) { this.korrelasjonId = korrelasjonId; }

    @Override
    public String toString() {
        return String.format("%s[id=%s, type=%s, tid=%s]", 
                           getClass().getSimpleName(), hendelseId, hendelseType, tidspunkt);
    }
}
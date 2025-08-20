package com.example.interviewprep.models.hendelser;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.example.interviewprep.models.SaksType;

/**
 * Hendelse når en ny sak opprettes
 * 
 * Trigger for andre systemer som:
 * - Arbeidsliste-system (opprett arbeidsoppgave)
 * - Varsel-system (send SMS/e-post til bruker)
 * - Statistikk-system (oppdater tellere)
 * - Automatisering-system (start datainnhenting)
 * 
 * Plassering: src/main/java/no/nav/integration/model/hendelser/SakOpprettetHendelse.java
 */
public class SakOpprettetHendelse extends BaseHendelse {
    
    @JsonProperty("sakId")
    private Long sakId;
    
    @JsonProperty("brukerId")
    private Long brukerId;
    
    @JsonProperty("brukerFnr")
    private String brukerFnr;  // Maskert
    
    @JsonProperty("saksType")
    private SaksType saksType;
    
    @JsonProperty("beskrivelse")
    private String beskrivelse;

    /**
     * Default konstruktør for JSON deserialisering
     */
    public SakOpprettetHendelse() {
        super("SAK_OPPRETTET");
    }

    /**
     * Konstruktør for å opprette hendelse
     * 
     * @param sakId ID på sak som ble opprettet
     * @param brukerId ID på bruker som eier saken
     * @param brukerFnr Brukerens fødselsnummer (maskeres)
     * @param saksType Type sak (DAGPENGER, SYKEPENGER, etc.)
     * @param beskrivelse Beskrivelse av saken
     */
    public SakOpprettetHendelse(Long sakId, Long brukerId, String brukerFnr, 
                               SaksType saksType, String beskrivelse) {
        this();
        this.sakId = sakId;
        this.brukerId = brukerId;
        this.brukerFnr = maskertFnr(brukerFnr);
        this.saksType = saksType;
        this.beskrivelse = beskrivelse;
    }

    /**
     * Masker fødselsnummer for personvern
     */
    private String maskertFnr(String fnr) {
        if (fnr == null || fnr.length() < 6) {
            return "***";
        }
        return fnr.substring(0, 6) + "*****";
    }

    // Getters og setters
    public Long getSakId() { return sakId; }
    public void setSakId(Long sakId) { this.sakId = sakId; }
    
    public Long getBrukerId() { return brukerId; }
    public void setBrukerId(Long brukerId) { this.brukerId = brukerId; }
    
    public String getBrukerFnr() { return brukerFnr; }
    public void setBrukerFnr(String brukerFnr) { this.brukerFnr = brukerFnr; }
    
    public SaksType getSaksType() { return saksType; }
    public void setSaksType(SaksType saksType) { this.saksType = saksType; }
    
    public String getBeskrivelse() { return beskrivelse; }
    public void setBeskrivelse(String beskrivelse) { this.beskrivelse = beskrivelse; }

    @Override
    public String toString() {
        return String.format("SakOpprettetHendelse[sakId=%s, type=%s, brukerId=%s]", 
                           sakId, saksType, brukerId);
    }
}
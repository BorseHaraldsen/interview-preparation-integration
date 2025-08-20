package com.example.interviewprep.models.hendelser;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.example.interviewprep.models.SaksType;
import java.util.Map;

/**
 * Hendelse når vedtak fattes for en sak
 * 
 * KRITISK HENDELSE - trigger følgende systemer:
 * - Utbetalingssystem (hvis innvilget)
 * - Dokumentgenerering (vedtaksbrev)
 * - Varsel-system (SMS/e-post til bruker)
 * - Statistikk og rapportering
 * - Compliance og audit systemer
 * 
 * Plassering: src/main/java/no/nav/integration/model/hendelser/VedtakFattetHendelse.java
 */
public class VedtakFattetHendelse extends BaseHendelse {
    
    @JsonProperty("sakId")
    private Long sakId;
    
    @JsonProperty("brukerId")
    private Long brukerId;
    
    @JsonProperty("saksType")
    private SaksType saksType;
    
    @JsonProperty("innvilget")
    private boolean innvilget;
    
    @JsonProperty("begrunnelse")
    private String begrunnelse;
    
    @JsonProperty("saksbehandler")
    private String saksbehandler;
    
    @JsonProperty("automatiskBehandlet")
    private boolean automatiskBehandlet;
    
    @JsonProperty("utbetalingsdata")
    private Map<String, Object> utbetalingsdata;

    /**
     * Default konstruktør for JSON deserialisering
     */
    public VedtakFattetHendelse() {
        super("VEDTAK_FATTET");
    }

    /**
     * Konstruktør for å opprette vedtak hendelse
     * 
     * @param sakId ID på sak det gjelder
     * @param brukerId ID på bruker som får vedtak
     * @param saksType Type sak (bestemmer utbetalingsregler)
     * @param innvilget true = innvilget, false = avslått
     * @param begrunnelse Begrunnelse for vedtaket
     * @param saksbehandler Hvem som fattet vedtaket
     * @param automatiskBehandlet Om vedtaket ble fattet automatisk
     */
    public VedtakFattetHendelse(Long sakId, Long brukerId, SaksType saksType, 
                               boolean innvilget, String begrunnelse, String saksbehandler, 
                               boolean automatiskBehandlet) {
        this();
        this.sakId = sakId;
        this.brukerId = brukerId;
        this.saksType = saksType;
        this.innvilget = innvilget;
        this.begrunnelse = begrunnelse;
        this.saksbehandler = saksbehandler;
        this.automatiskBehandlet = automatiskBehandlet;
        
        // Sett utbetalingsdata hvis innvilget
        if (innvilget) {
            this.utbetalingsdata = opprettUtbetalingsdata(saksType);
        }
    }

    /**
     * Opprett utbetalingsdata basert på sakstype
     * I praksis ville dette vært mye mer kompleks med beregningsregler
     */
    private Map<String, Object> opprettUtbetalingsdata(SaksType saksType) {
        double beløp = switch (saksType) {
            case DAGPENGER -> 15000.0;
            case SYKEPENGER -> 25000.0;
            case AAP -> 20000.0;
            case UFORETRYGD -> 30000.0;
            case ALDERSPENSJON -> 18000.0;
            case BARNETRYGD -> 1054.0;
        };

        return Map.of(
                "månedligBeløp", beløp,
                "valuta", "NOK",
                "utbetalingType", saksType.name(),
                "prioritet", "NORMAL"
        );
    }

    // Getters og setters
    public Long getSakId() { return sakId; }
    public void setSakId(Long sakId) { this.sakId = sakId; }
    
    public Long getBrukerId() { return brukerId; }
    public void setBrukerId(Long brukerId) { this.brukerId = brukerId; }
    
    public SaksType getSaksType() { return saksType; }
    public void setSaksType(SaksType saksType) { this.saksType = saksType; }
    
    public boolean isInnvilget() { return innvilget; }
    public void setInnvilget(boolean innvilget) { this.innvilget = innvilget; }
    
    public String getBegrunnelse() { return begrunnelse; }
    public void setBegrunnelse(String begrunnelse) { this.begrunnelse = begrunnelse; }
    
    public String getSaksbehandler() { return saksbehandler; }
    public void setSaksbehandler(String saksbehandler) { this.saksbehandler = saksbehandler; }
    
    public boolean isAutomatiskBehandlet() { return automatiskBehandlet; }
    public void setAutomatiskBehandlet(boolean automatiskBehandlet) { this.automatiskBehandlet = automatiskBehandlet; }
    
    public Map<String, Object> getUtbetalingsdata() { return utbetalingsdata; }
    public void setUtbetalingsdata(Map<String, Object> utbetalingsdata) { this.utbetalingsdata = utbetalingsdata; }

    @Override
    public String toString() {
        return String.format("VedtakFattetHendelse[sakId=%s, innvilget=%s, automatisk=%s, beløp=%s]", 
                           sakId, innvilget, automatiskBehandlet, 
                           utbetalingsdata != null ? utbetalingsdata.get("månedligBeløp") : "N/A");
    }
}
    package com.example.interviewprep.models.hendelser;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.example.interviewprep.models.SaksStatus;

/**
 * Hendelse når saksstatus endres
 * Kritisk for saksflyt og integrasjon mellom systemer
 */
class SaksStatusEndretHendelse extends BaseHendelse {
    
    @JsonProperty("sakId")
    private Long sakId;
    
    @JsonProperty("brukerId")
    private Long brukerId;
    
    @JsonProperty("gammelStatus")
    private SaksStatus gammelStatus;
    
    @JsonProperty("nyStatus")
    private SaksStatus nyStatus;
    
    @JsonProperty("endretAv")
    private String endretAv;
    
    @JsonProperty("begrunnelse")
    private String begrunnelse;

    public SaksStatusEndretHendelse() {
        super("SAKS_STATUS_ENDRET");
    }

    public SaksStatusEndretHendelse(Long sakId, Long brukerId, SaksStatus gammelStatus, 
                                   SaksStatus nyStatus, String endretAv, String begrunnelse) {
        this();
        this.sakId = sakId;
        this.brukerId = brukerId;
        this.gammelStatus = gammelStatus;
        this.nyStatus = nyStatus;
        this.endretAv = endretAv;
        this.begrunnelse = begrunnelse;
    }

    // Getters og setters
    public Long getSakId() { return sakId; }
    public void setSakId(Long sakId) { this.sakId = sakId; }
    public Long getBrukerId() { return brukerId; }
    public void setBrukerId(Long brukerId) { this.brukerId = brukerId; }
    public SaksStatus getGammelStatus() { return gammelStatus; }
    public void setGammelStatus(SaksStatus gammelStatus) { this.gammelStatus = gammelStatus; }
    public SaksStatus getNyStatus() { return nyStatus; }
    public void setNyStatus(SaksStatus nyStatus) { this.nyStatus = nyStatus; }
    public String getEndretAv() { return endretAv; }
    public void setEndretAv(String endretAv) { this.endretAv = endretAv; }
    public String getBegrunnelse() { return begrunnelse; }
    public void setBegrunnelse(String begrunnelse) { this.begrunnelse = begrunnelse; }
}

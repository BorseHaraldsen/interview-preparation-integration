package no.nav.folkeregister.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * XML response structure from Folkeregister - deliberately different from our main system.
 * Uses Norwegian field names and XML format to create integration complexity.
 */
@JacksonXmlRootElement(localName = "person")
public class PersonXmlResponse {
    
    @JacksonXmlProperty(localName = "fødselsnummer")
    private String fødselsnummer;
    
    @JacksonXmlProperty(localName = "personnavn")
    private PersonnavnXml personnavn;
    
    @JacksonXmlProperty(localName = "fødselsdato")
    private String fødselsdato; // Format: dd.MM.yyyy (Norwegian standard)
    
    @JacksonXmlProperty(localName = "kjønn")
    private String kjønn; // "M" or "K" (Mann/Kvinne)
    
    @JacksonXmlProperty(localName = "sivilstand")
    private String sivilstand;
    
    @JacksonXmlProperty(localName = "bostedsadresse")
    private AdresseXml bostedsadresse;
    
    @JacksonXmlProperty(localName = "statsborgerskap")
    private String statsborgerskap;
    
    @JacksonXmlProperty(localName = "status")
    private PersonStatusXml status;

    // Nested name structure - more complex than flat JSON
    public static class PersonnavnXml {
        @JacksonXmlProperty(localName = "fornavn")
        private String fornavn;
        
        @JacksonXmlProperty(localName = "mellomnavn")
        private String mellomnavn;
        
        @JacksonXmlProperty(localName = "etternavn")
        private String etternavn;
        
        @JacksonXmlProperty(localName = "fulltNavn")
        private String fulltNavn;

        // Constructors
        public PersonnavnXml() {}
        
        public PersonnavnXml(String fornavn, String mellomnavn, String etternavn) {
            this.fornavn = fornavn;
            this.mellomnavn = mellomnavn;
            this.etternavn = etternavn;
            this.fulltNavn = buildFulltNavn(fornavn, mellomnavn, etternavn);
        }
        
        private String buildFulltNavn(String fornavn, String mellomnavn, String etternavn) {
            StringBuilder fullName = new StringBuilder(fornavn);
            if (mellomnavn != null && !mellomnavn.trim().isEmpty()) {
                fullName.append(" ").append(mellomnavn);
            }
            fullName.append(" ").append(etternavn);
            return fullName.toString();
        }

        // Getters and setters
        public String getFornavn() { return fornavn; }
        public void setFornavn(String fornavn) { this.fornavn = fornavn; }
        public String getMellomnavn() { return mellomnavn; }
        public void setMellomnavn(String mellomnavn) { this.mellomnavn = mellomnavn; }
        public String getEtternavn() { return etternavn; }
        public void setEtternavn(String etternavn) { this.etternavn = etternavn; }
        public String getFulltNavn() { return fulltNavn; }
        public void setFulltNavn(String fulltNavn) { this.fulltNavn = fulltNavn; }
    }
    
    // Complex nested address structure  
    public static class AdresseXml {
        @JacksonXmlProperty(localName = "gateadresse")
        private GateadresseXml gateadresse;
        
        @JacksonXmlProperty(localName = "poststed")
        private PoststedXml poststed;
        
        @JacksonXmlProperty(localName = "kommune")
        private KommuneXml kommune;

        public static class GateadresseXml {
            @JacksonXmlProperty(localName = "gatenavn")
            private String gatenavn;
            
            @JacksonXmlProperty(localName = "husnummer")
            private String husnummer;
            
            @JacksonXmlProperty(localName = "husbokstav")
            private String husbokstav;
            
            @JacksonXmlProperty(localName = "fullAdresse")
            private String fullAdresse;

            public GateadresseXml() {}
            
            public GateadresseXml(String gatenavn, String husnummer, String husbokstav) {
                this.gatenavn = gatenavn;
                this.husnummer = husnummer;
                this.husbokstav = husbokstav;
                this.fullAdresse = buildFullAdresse();
            }
            
            private String buildFullAdresse() {
                StringBuilder addr = new StringBuilder(gatenavn).append(" ").append(husnummer);
                if (husbokstav != null && !husbokstav.trim().isEmpty()) {
                    addr.append(husbokstav);
                }
                return addr.toString();
            }

            // Getters and setters
            public String getGatenavn() { return gatenavn; }
            public void setGatenavn(String gatenavn) { this.gatenavn = gatenavn; }
            public String getHusnummer() { return husnummer; }
            public void setHusnummer(String husnummer) { this.husnummer = husnummer; }
            public String getHusbokstav() { return husbokstav; }
            public void setHusbokstav(String husbokstav) { this.husbokstav = husbokstav; }
            public String getFullAdresse() { return fullAdresse; }
            public void setFullAdresse(String fullAdresse) { this.fullAdresse = fullAdresse; }
        }

        public static class PoststedXml {
            @JacksonXmlProperty(localName = "postnummer")
            private String postnummer;
            
            @JacksonXmlProperty(localName = "poststedsnavn")
            private String poststedsnavn;

            public PoststedXml() {}
            public PoststedXml(String postnummer, String poststedsnavn) {
                this.postnummer = postnummer;
                this.poststedsnavn = poststedsnavn;
            }

            // Getters and setters
            public String getPostnummer() { return postnummer; }
            public void setPostnummer(String postnummer) { this.postnummer = postnummer; }
            public String getPoststedsnavn() { return poststedsnavn; }
            public void setPoststedsnavn(String poststedsnavn) { this.poststedsnavn = poststedsnavn; }
        }

        public static class KommuneXml {
            @JacksonXmlProperty(localName = "kommunenummer")
            private String kommunenummer;
            
            @JacksonXmlProperty(localName = "kommunenavn")
            private String kommunenavn;

            public KommuneXml() {}
            public KommuneXml(String kommunenummer, String kommunenavn) {
                this.kommunenummer = kommunenummer;
                this.kommunenavn = kommunenavn;
            }

            // Getters and setters
            public String getKommunenummer() { return kommunenummer; }
            public void setKommunenummer(String kommunenummer) { this.kommunenummer = kommunenummer; }
            public String getKommunenavn() { return kommunenavn; }
            public void setKommunenavn(String kommunenavn) { this.kommunenavn = kommunenavn; }
        }

        // Constructors and getters/setters for AdresseXml
        public AdresseXml() {}
        
        public AdresseXml(GateadresseXml gateadresse, PoststedXml poststed, KommuneXml kommune) {
            this.gateadresse = gateadresse;
            this.poststed = poststed;
            this.kommune = kommune;
        }

        public GateadresseXml getGateadresse() { return gateadresse; }
        public void setGateadresse(GateadresseXml gateadresse) { this.gateadresse = gateadresse; }
        public PoststedXml getPoststed() { return poststed; }
        public void setPoststed(PoststedXml poststed) { this.poststed = poststed; }
        public KommuneXml getKommune() { return kommune; }
        public void setKommune(KommuneXml kommune) { this.kommune = kommune; }
    }
    
    // Person status with Norwegian terminology
    public static class PersonStatusXml {
        @JacksonXmlProperty(localName = "erDød")
        private boolean erDød;
        
        @JacksonXmlProperty(localName = "dødsdato")
        private String dødsdato; // Format: dd.MM.yyyy if applicable
        
        @JacksonXmlProperty(localName = "erBosatt")
        private boolean erBosatt;
        
        @JacksonXmlProperty(localName = "sistOppdatert")
        private String sistOppdatert; // Format: dd.MM.yyyy HH:mm:ss

        public PersonStatusXml() {}
        
        public PersonStatusXml(boolean erDød, String dødsdato, boolean erBosatt, String sistOppdatert) {
            this.erDød = erDød;
            this.dødsdato = dødsdato;
            this.erBosatt = erBosatt;
            this.sistOppdatert = sistOppdatert;
        }

        // Getters and setters
        public boolean isErDød() { return erDød; }
        public void setErDød(boolean erDød) { this.erDød = erDød; }
        public String getDødsdato() { return dødsdato; }
        public void setDødsdato(String dødsdato) { this.dødsdato = dødsdato; }
        public boolean isErBosatt() { return erBosatt; }
        public void setErBosatt(boolean erBosatt) { this.erBosatt = erBosatt; }
        public String getSistOppdatert() { return sistOppdatert; }
        public void setSistOppdatert(String sistOppdatert) { this.sistOppdatert = sistOppdatert; }
    }

    // Main class constructors and getters/setters
    public PersonXmlResponse() {}
    
    public PersonXmlResponse(String fødselsnummer, PersonnavnXml personnavn, String fødselsdato, 
                            String kjønn, String sivilstand, AdresseXml bostedsadresse, 
                            String statsborgerskap, PersonStatusXml status) {
        this.fødselsnummer = fødselsnummer;
        this.personnavn = personnavn;
        this.fødselsdato = fødselsdato;
        this.kjønn = kjønn;
        this.sivilstand = sivilstand;
        this.bostedsadresse = bostedsadresse;
        this.statsborgerskap = statsborgerskap;
        this.status = status;
    }

    // Getters and setters
    public String getFødselsnummer() { return fødselsnummer; }
    public void setFødselsnummer(String fødselsnummer) { this.fødselsnummer = fødselsnummer; }
    public PersonnavnXml getPersonnavn() { return personnavn; }
    public void setPersonnavn(PersonnavnXml personnavn) { this.personnavn = personnavn; }
    public String getFødselsdato() { return fødselsdato; }
    public void setFødselsdato(String fødselsdato) { this.fødselsdato = fødselsdato; }
    public String getKjønn() { return kjønn; }
    public void setKjønn(String kjønn) { this.kjønn = kjønn; }
    public String getSivilstand() { return sivilstand; }
    public void setSivilstand(String sivilstand) { this.sivilstand = sivilstand; }
    public AdresseXml getBostedsadresse() { return bostedsadresse; }
    public void setBostedsadresse(AdresseXml bostedsadresse) { this.bostedsadresse = bostedsadresse; }
    public String getStatsborgerskap() { return statsborgerskap; }
    public void setStatsborgerskap(String statsborgerskap) { this.statsborgerskap = statsborgerskap; }
    public PersonStatusXml getStatus() { return status; }
    public void setStatus(PersonStatusXml status) { this.status = status; }
}
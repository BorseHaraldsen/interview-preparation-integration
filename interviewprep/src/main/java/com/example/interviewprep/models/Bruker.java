package com.example.interviewprep.models;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Bruker entitet - representerer en NAV bruker
 * 
 * @Entity: JPA annotasjon som gjør dette til en database-tabell
 * Dette er grunnleggende for database-integrasjon som NAV trenger
 */
@Entity
@Table(name = "bruker")
public class Bruker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Fødselsnummer - kritisk identifikator i NAV-systemer
     * @Pattern validerer at det er et gyldig 11-sifret fnr
     */
    @Column(unique = true, nullable = false, length = 11)
    @NotBlank(message = "Fødselsnummer kan ikke være tomt")
    @Pattern(regexp = "\\d{11}", message = "Fødselsnummer må være 11 siffer")
    private String fodselsnummer;

    @Column(nullable = false)
    @NotBlank(message = "Navn kan ikke være tomt")
    private String navn;

    @Column(nullable = false)
    @NotBlank(message = "Adresse kan ikke være tom")
    private String adresse;

    @Column(name = "opprettet_tid")
    private LocalDateTime opprettetTid;

    /**
     * OneToMany relasjon til saker
     * Dette viser hvordan vi håndterer relasjoner mellom systemer
     * I praksis ville dette vært API-kall til andre tjenester
     */
    @OneToMany(mappedBy = "bruker", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<Sak> saker;

    // Konstruktører
    public Bruker() {
        this.opprettetTid = LocalDateTime.now();
    }

    public Bruker(String fodselsnummer, String navn, String adresse) {
        this();
        this.fodselsnummer = fodselsnummer;
        this.navn = navn;
        this.adresse = adresse;
    }

    // Getters og Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFodselsnummer() { return fodselsnummer; }
    public void setFodselsnummer(String fodselsnummer) { this.fodselsnummer = fodselsnummer; }

    public String getNavn() { return navn; }
    public void setNavn(String navn) { this.navn = navn; }

    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }

    public LocalDateTime getOpprettetTid() { return opprettetTid; }
    public void setOpprettetTid(LocalDateTime opprettetTid) { this.opprettetTid = opprettetTid; }

    public List<Sak> getSaker() { return saker; }
    public void setSaker(List<Sak> saker) { this.saker = saker; }

    @Override
    public String toString() {
        return "Bruker{" +
                "id=" + id +
                ", navn='" + navn + '\'' +
                ", opprettetTid=" + opprettetTid +
                '}';
    }
}
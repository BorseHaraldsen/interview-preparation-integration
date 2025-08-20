package com.example.interviewprep.models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Sak entitet - representerer en sak i NAV-systemet
 * Dette er kjernen av NAV's forretningslogikk
 */
@Entity
@Table(name = "sak")
public class Sak {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Referanse til bruker som eier saken
     * ManyToOne: Mange saker kan tilhøre samme bruker
     * Dette er typisk i NAV hvor en person kan ha flere saker
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bruker_id", nullable = false)
    @NotNull(message = "Sak må tilhøre en bruker")
    @JsonBackReference
    private Bruker bruker;

    /**
     * Sakstype - f.eks. DAGPENGER, SYKEPENGER, AAP
     * Dette er enum for å sikre datakvalitet
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull(message = "Sakstype må være spesifisert")
    private SaksType type;

    /**
     * Status på saken - viktig for saksflyt
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SaksStatus status;

    @Column(nullable = false)
    @NotBlank(message = "Beskrivelse kan ikke være tom")
    private String beskrivelse;

    @Column(name = "opprettet_tid")
    private LocalDateTime opprettetTid;

    @Column(name = "sist_endret")
    private LocalDateTime sistEndret;

    // Konstruktører
    public Sak() {
        this.opprettetTid = LocalDateTime.now();
        this.sistEndret = LocalDateTime.now();
        this.status = SaksStatus.MOTTATT;
    }

    public Sak(Bruker bruker, SaksType type, String beskrivelse) {
        this();
        this.bruker = bruker;
        this.type = type;
        this.beskrivelse = beskrivelse;
    }

    /**
     * Business logic: Oppdater status og timestamp
     * Dette viser hvordan vi kan ha forretningslogikk i modellen
     */
    public void oppdaterStatus(SaksStatus nyStatus) {
        this.status = nyStatus;
        this.sistEndret = LocalDateTime.now();
    }

    // Getters og Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Bruker getBruker() { return bruker; }
    public void setBruker(Bruker bruker) { this.bruker = bruker; }

    public SaksType getType() { return type; }
    public void setType(SaksType type) { this.type = type; }

    public SaksStatus getStatus() { return status; }
    public void setStatus(SaksStatus status) { 
        this.status = status;
        this.sistEndret = LocalDateTime.now();
    }

    public String getBeskrivelse() { return beskrivelse; }
    public void setBeskrivelse(String beskrivelse) { this.beskrivelse = beskrivelse; }

    public LocalDateTime getOpprettetTid() { return opprettetTid; }
    public void setOpprettetTid(LocalDateTime opprettetTid) { this.opprettetTid = opprettetTid; }

    public LocalDateTime getSistEndret() { return sistEndret; }
    public void setSistEndret(LocalDateTime sistEndret) { this.sistEndret = sistEndret; }

    @Override
    public String toString() {
        return "Sak{" +
                "id=" + id +
                ", type=" + type +
                ", status=" + status +
                ", opprettetTid=" + opprettetTid +
                '}';
    }
}

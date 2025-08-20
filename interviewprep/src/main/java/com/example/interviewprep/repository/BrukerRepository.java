package com.example.interviewprep.repository;

import  com.example.interviewprep.models.Bruker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Bruker entitet
 * 
 * @Repository: Spring annotasjon som markerer dette som en data access komponent
 * JpaRepository<Bruker, Long>: Gir oss gratis CRUD operasjoner
 * 
 * Dette er Repository Pattern - en viktig arkitekturell pattern i integrasjoner
 * Separerer database-logikk fra forretningslogikk
 */
@Repository
public interface BrukerRepository extends JpaRepository<Bruker, Long> {

    /**
     * Finn bruker basert på fødselsnummer
     * Dette er en typisk oppslag i NAV-systemer
     * 
     * Spring Data JPA genererer SQL automatisk basert på metodenavn:
     * SELECT * FROM bruker WHERE fodselsnummer = ?
     */
    Optional<Bruker> findByFodselsnummer(String fodselsnummer);

    /**
     * Sjekk om bruker eksisterer med gitt fødselsnummer
     * Returnerer boolean - effektivt for valideringer
     */
    boolean existsByFodselsnummer(String fodselsnummer);

    /**
     * Finn brukere som er registrert etter en gitt dato
     * Brukes ofte i rapporter og statistikk
     */
    List<Bruker> findByOpprettetTidAfter(LocalDateTime dato);

    /**
     * Custom query med @Query annotasjon
     * Viser hvordan vi kan skrive egne SQL spørringer når automatisk generering ikke holder
     * 
     * Dette er nyttig for komplekse spørringer som ofte trengs i integrasjoner
     */
    @Query("SELECT b FROM Bruker b WHERE b.navn LIKE %:navn% ORDER BY b.opprettetTid DESC")
    List<Bruker> finnBrukereByNavnSoek(@Param("navn") String navn);

    /**
     * Native SQL query eksempel
     * Noen ganger trenger vi full kontroll over SQL
     * Dette er relevant når vi integrerer med legacy systemer som Oracle EBS
     */
    @Query(value = "SELECT COUNT(*) FROM bruker WHERE DATE(opprettet_tid) = CURRENT_DATE", 
           nativeQuery = true)
    long tellAntallBrukereRegistrertIdag();

    /**
     * Finn brukere med aktive saker
     * Dette viser en join mellom tabeller - vanlig i integrasjoner
     */
    @Query("SELECT DISTINCT b FROM Bruker b " +
           "JOIN b.saker s " +
           "WHERE s.status IN ('MOTTATT', 'UNDER_BEHANDLING', 'VENTER_DOKUMENTASJON')")
    List<Bruker> finnBrukereMedAktiveSaker();
}
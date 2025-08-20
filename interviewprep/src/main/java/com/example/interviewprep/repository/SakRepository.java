package com.example.interviewprep.repository;
import  com.example.interviewprep.models.Bruker;
import com.example.interviewprep.models.Sak;
import com.example.interviewprep.models.SaksStatus;
import com.example.interviewprep.models.SaksType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Sak entitet
 * Viser mer avanserte database-operasjoner som er typiske i NAV-systemer
 */
@Repository
public interface SakRepository extends JpaRepository<Sak, Long> {

    /**
     * Finn alle saker for en bruker basert på fødselsnummer
     * Dette er en typisk integrasjons-oppslag mellom systemer
     */
    @Query("SELECT s FROM Sak s WHERE s.bruker.fodselsnummer = :fnr ORDER BY s.opprettetTid DESC")
    List<Sak> finnSakerByBrukerFnr(@Param("fnr") String fodselsnummer);

    /**
     * Finn saker basert på status
     * Brukes for å identifisere saker som trenger behandling
     */
    List<Sak> findByStatusOrderByOpprettetTidAsc(SaksStatus status);

    /**
     * Finn saker av en bestemt type som er aktive
     * Viser hvordan vi kan kombinere flere kriterier
     */
    List<Sak> findByTypeAndStatusIn(SaksType type, List<SaksStatus> statuser);

    /**
     * Finn gamle saker som kan arkiveres
     * Viktig for dataforvaltning i store systemer som NAV
     */
    @Query("SELECT s FROM Sak s WHERE s.status = 'AVSLUTTET' AND s.sistEndret < :dato")
    List<Sak> finnGamleSakerForArkivering(@Param("dato") LocalDateTime arkiveringsDato);

    /**
     * Statistikk: Tell saker per type og status
     * Brukes ofte i rapporter og dashboards
     */
    @Query("SELECT s.type, s.status, COUNT(s) FROM Sak s GROUP BY s.type, s.status")
    List<Object[]> tellSakerPerTypeOgStatus();

    /**
     * Finn saker som har vært under behandling for lenge
     * Business logic: Identifiser saker som trenger oppfølging
     */
    @Query("SELECT s FROM Sak s WHERE s.status = 'UNDER_BEHANDLING' " +
           "AND s.sistEndret < :tidsfrist ORDER BY s.sistEndret ASC")
    List<Sak> finnSakerSomTrengerOppfolging(@Param("tidsfrist") LocalDateTime tidsfrist);

    /**
     * Bulk oppdatering av status
     * @Modifying: Indikerer at dette er en write-operasjon, ikke read
     * Effektivt for å oppdatere mange records samtidig
     */
    @Modifying
    @Query("UPDATE Sak s SET s.status = :nyStatus, s.sistEndret = :tidspunkt " +
           "WHERE s.id IN :sakIds")
    int oppdaterStatusForSaker(@Param("sakIds") List<Long> sakIds, 
                               @Param("nyStatus") SaksStatus nyStatus,
                               @Param("tidspunkt") LocalDateTime tidspunkt);

    /**
     * Performance query: Finn saker som er endret siden sist synkronisering
     * Viktig for integrasjoner som må holde systemer synkronisert
     */
    @Query("SELECT s FROM Sak s WHERE s.sistEndret > :sisteSynk ORDER BY s.sistEndret ASC")
    List<Sak> finnEndredeSaker(@Param("sisteSynk") LocalDateTime sisteSynkronisering);

    /**
     * Custom query for rapportering
     * Viser hvor kraftig Spring Data JPA kan være for komplekse spørringer
     */
    @Query("SELECT " +
           "s.type as saksType, " +
           "COUNT(s) as antall, " +
           "AVG(TIMESTAMPDIFF(DAY, s.opprettetTid, s.sistEndret)) as gjennomsnittligBehandlingstid " +
           "FROM Sak s " +
           "WHERE s.opprettetTid >= :fradato " +
           "GROUP BY s.type")
    List<Object[]> hentBehandlingsstatistikk(@Param("fradate") LocalDateTime fraDato);

    /**
     * Finn saker som kan automatisk behandles
     * Eksempel på business logic i database-lag
     */
    @Query("SELECT s FROM Sak s WHERE " +
           "s.type = 'DAGPENGER' AND " +
           "s.status = 'MOTTATT' AND " +
           "s.opprettetTid > :dato AND " +
           "s.beskrivelse NOT LIKE '%kompleks%'")
    List<Sak> finnSakerForAutomatiskBehandling(@Param("dato") LocalDateTime dato);
}
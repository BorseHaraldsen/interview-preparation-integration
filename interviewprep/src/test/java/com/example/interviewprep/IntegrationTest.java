package com.example.interviewprep;

import com.example.interviewprep.models.Bruker;
import com.example.interviewprep.models.Sak;
import com.example.interviewprep.models.SaksStatus;
import com.example.interviewprep.models.SaksType;
import com.example.interviewprep.service.BrukerService;
import com.example.interviewprep.service.SaksService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integrasjonstest som tester hele applikasjonen
 * 
 * Dette viser hvordan vi tester integrasjoner mellom lag
 * @SpringBootTest laster hele Spring contexten
 * @ActiveProfiles("test") bruker test-konfigurasjon
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class IntegrationTest {

    @Autowired
    private BrukerService brukerService;

    @Autowired
    private SaksService saksService;

    @Test
    @Transactional
    void skalOppretteBrukerOgSak() {
        // Given - Opprett en testbruker
        String fnr = "11223344556";
        String navn = "Test Testesen";
        String adresse = "Testveien 1, 0001 Oslo";

        // When - Opprett bruker
        Bruker bruker = brukerService.opprettBruker(fnr, navn, adresse);

        // Then - Verifiser bruker
        assertNotNull(bruker.getId());
        assertEquals(fnr, bruker.getFodselsnummer());
        assertEquals(navn, bruker.getNavn());
        assertEquals(adresse, bruker.getAdresse());

        // When - Opprett sak for brukeren
        String beskrivelse = "Test søknad om dagpenger";
        Sak sak = saksService.opprettSak(fnr, SaksType.DAGPENGER, beskrivelse);

        // Then - Verifiser sak
        assertNotNull(sak.getId());
        assertEquals(SaksType.DAGPENGER, sak.getType());
        assertEquals(SaksStatus.MOTTATT, sak.getStatus());
        assertEquals(beskrivelse, sak.getBeskrivelse());
        assertEquals(bruker.getId(), sak.getBruker().getId());

        System.out.println("✅ Test: Bruker og sak opprettet successfully");
        System.out.println("   Bruker ID: " + bruker.getId() + ", Sak ID: " + sak.getId());
    }

    @Test
    @Transactional
    void skalBehandleSakFullstendig() {
        // Given - Opprett bruker og sak (bruker eksisterende testdata)
        String fnr = "12345678901"; // Fra data.sql
        List<Sak> eksisterendeSaker = saksService.hentSakerForBruker(fnr);
        assertFalse(eksisterendeSaker.isEmpty(), "Skal ha eksisterende saker fra testdata");

        Sak sak = eksisterendeSaker.stream()
                .filter(s -> s.getStatus() == SaksStatus.MOTTATT)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Skal finne en mottatt sak"));

        Long sakId = sak.getId();
        System.out.println("🔄 Starter behandling av sak: " + sakId);

        // When - Start saksbehandling
        Sak behandlingSak = saksService.startSaksbehandling(sakId);

        // Then - Verifiser status endret
        assertEquals(SaksStatus.UNDER_BEHANDLING, behandlingSak.getStatus());
        System.out.println("✅ Saksbehandling startet");

        // When - Ferdigstill sak
        boolean innvilget = true;
        String begrunnelse = "Alle vilkår er oppfylt";
        Sak ferdigSak = saksService.ferdigstillSak(sakId, innvilget, begrunnelse);

        // Then - Verifiser vedtak
        assertEquals(SaksStatus.VEDTAK_FATTET, ferdigSak.getStatus());
        assertTrue(ferdigSak.getBeskrivelse().contains("INNVILGET"));
        assertTrue(ferdigSak.getBeskrivelse().contains(begrunnelse));

        System.out.println("✅ Test: Sak behandlet fullstendig fra MOTTATT til VEDTAK_FATTET");
    }

    @Test
    void skalFinneBrukereOgSaker() {
        // Given - Bruk testdata som allerede er lastet

        // When - Hent alle brukere
        List<Bruker> alleBrukere = brukerService.hentAlleBrukere();

        // Then - Verifiser at vi har testdata
        assertFalse(alleBrukere.isEmpty(), "Skal ha brukere fra testdata");
        System.out.println("📊 Antall brukere i databasen: " + alleBrukere.size());

        // When - Hent saker for første bruker
        Bruker forsteBruker = alleBrukere.get(0);
        List<Sak> brukersSaker = saksService.hentSakerForBruker(forsteBruker.getFodselsnummer());

        // Then - Verifiser saker
        assertFalse(brukersSaker.isEmpty(), "Bruker skal ha saker");
        System.out.println("📋 Antall saker for " + forsteBruker.getNavn() + ": " + brukersSaker.size());

        // When - Hent arbeidsliste
        List<Sak> arbeidsliste = saksService.hentSakerSomTrengerBehandling();
        System.out.println("📝 Saker i arbeidslisten: " + arbeidsliste.size());

        // Then - Verifiser at alle saker i arbeidslisten har riktig status
        for (Sak sak : arbeidsliste) {
            assertEquals(SaksStatus.MOTTATT, sak.getStatus());
        }

        System.out.println("✅ Test: Database queries fungerer korrekt");
    }

    @Test
    void skalValidereForretningsregler() {
        // Given
        String fnr = "99887766554";
        String navn = "Validation Tester";
        String adresse = "Validasjonsgate 1";

        // When/Then - Test duplikat bruker
        brukerService.opprettBruker(fnr, navn, adresse);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            brukerService.opprettBruker(fnr, navn, adresse); // Duplikat
        });
        assertTrue(exception.getMessage().contains("eksisterer allerede"));
        System.out.println("✅ Forretningsregel: Duplikat bruker blokkert");

        // When/Then - Test ugyldig fødselsnummer
        assertThrows(IllegalArgumentException.class, () -> {
            brukerService.opprettBruker("123", "Test", "Test"); // For kort fnr
        });
        System.out.println("✅ Forretningsregel: Ugyldig fødselsnummer blokkert");

        // When/Then - Test aktiv sak av samme type
        saksService.opprettSak(fnr, SaksType.DAGPENGER, "Første søknad");
        
        IllegalStateException sakException = assertThrows(IllegalStateException.class, () -> {
            saksService.opprettSak(fnr, SaksType.DAGPENGER, "Duplikat søknad");
        });
        assertTrue(sakException.getMessage().contains("allerede en aktiv sak"));
        System.out.println("✅ Forretningsregel: Duplikat aktiv sak blokkert");
    }

    @Test
    void skalHandtereAutomatiskBehandling() {
        // Given - Opprett sak som kan behandles automatisk
        String fnr = "55667788990";
        brukerService.opprettBruker(fnr, "Auto Tester", "Autogate 1");
        saksService.opprettSak(fnr, SaksType.DAGPENGER, "Standard søknad automatisk behandling");

        // When - Kjør automatisk behandling
        List<Sak> behandledeSaker = saksService.automatiskBehandlingsEnkleSaker();

        // Then - Verifiser at saker ble behandlet
        System.out.println("🤖 Automatisk behandling: " + behandledeSaker.size() + " saker behandlet");

        // Verifiser at vi har saker som ble behandlet automatisk
        assertFalse(behandledeSaker.isEmpty(), "Skal ha behandlet minst én sak automatisk");

        System.out.println("✅ Test: Automatisk behandling fungerer");
    }

    @Test
    void skalSokeBrukere() {
        // When - Søk brukere basert på navn
        List<Bruker> olaResults = brukerService.sokBrukere("Ola");
        List<Bruker> hansenResults = brukerService.sokBrukere("Hansen");

        // Then - Verifiser søkeresultater
        assertFalse(olaResults.isEmpty(), "Skal finne brukere som inneholder 'Ola'");
        assertFalse(hansenResults.isEmpty(), "Skal finne brukere som inneholder 'Hansen'");

        System.out.println("🔍 Søk på 'Ola': " + olaResults.size() + " resultater");
        System.out.println("🔍 Søk på 'Hansen': " + hansenResults.size() + " resultater");

        // Verifiser at søkeresultater inneholder søkeordet
        for (Bruker bruker : olaResults) {
            assertTrue(bruker.getNavn().toLowerCase().contains("ola"),
                    "Søkeresultat skal inneholde søkeord");
        }

        System.out.println("✅ Test: Brukersøk fungerer korrekt");
    }
}

/**
 * Test som viser hvordan vi kan teste API lag
 * Integrerer Spring MVC Test med faktisk database
 */
// Vi kan legge til WebMvcTest senere hvis ønskelig
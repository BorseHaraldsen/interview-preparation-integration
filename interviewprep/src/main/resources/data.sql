-- data.sql - Testdata for NAV Integration Demo
-- Plassering: src/main/resources/data.sql
-- Denne filen kjøres automatisk ved oppstart av Spring Boot

-- =============================================================================
-- TESTBRUKERE
-- =============================================================================
INSERT INTO bruker (fodselsnummer, navn, adresse, opprettet_tid) VALUES 
('12345678901', 'Ola Nordmann', 'Storgata 1, 0001 Oslo', CURRENT_TIMESTAMP),
('98765432109', 'Kari Hansen', 'Bjørnstadveien 12, 1234 Bergen', CURRENT_TIMESTAMP),
('11223344556', 'Per Johansen', 'Elveveien 34, 5678 Trondheim', CURRENT_TIMESTAMP),
('66778899001', 'Anna Larsen', 'Parkveien 56, 9012 Stavanger', CURRENT_TIMESTAMP),
('55443322110', 'Erik Andersen', 'Skolegata 78, 3456 Tromsø', CURRENT_TIMESTAMP);

-- =============================================================================
-- TESTSAKER - Realistiske NAV saker
-- =============================================================================

-- Ola Nordmann sine saker
INSERT INTO sak (bruker_id, type, status, beskrivelse, opprettet_tid, sist_endret) VALUES 
(1, 'DAGPENGER', 'MOTTATT', 'Søknad om dagpenger etter permittering fra bedrift', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(1, 'SYKEPENGER', 'VEDTAK_FATTET', 'Sykmelding grunnet ryggproblemer - 14 dager', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Kari Hansen sine saker  
INSERT INTO sak (bruker_id, type, status, beskrivelse, opprettet_tid, sist_endret) VALUES 
(2, 'BARNETRYGD', 'UNDER_BEHANDLING', 'Søknad om barnetrygd for nyfødt barn', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 'AAP', 'VENTER_DOKUMENTASJON', 'Arbeidsavklaringspenger - mangler legeerklæring', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Per Johansen sine saker
INSERT INTO sak (bruker_id, type, status, beskrivelse, opprettet_tid, sist_endret) VALUES 
(3, 'UFORETRYGD', 'UNDER_BEHANDLING', 'Søknad om uføretrygd etter arbeidsulykke', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Anna Larsen sine saker
INSERT INTO sak (bruker_id, type, status, beskrivelse, opprettet_tid, sist_endret) VALUES 
(4, 'ALDERSPENSJON', 'MOTTATT', 'Søknad om alderspensjon ved 67 år', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 'DAGPENGER', 'AVVIST', 'Søknad avvist - ikke oppfylt krav til arbeidsperiode', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Erik Andersen sine saker
INSERT INTO sak (bruker_id, type, status, beskrivelse, opprettet_tid, sist_endret) VALUES 
(5, 'SYKEPENGER', 'UTBETALT', 'Sykepenger utbetalt for mai måned', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(5, 'DAGPENGER', 'AVSLUTTET', 'Dagpengeperiode avsluttet - bruker startet i ny jobb', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- =============================================================================
-- ELDRE SAKER FOR TESTING AV AUTOMATISK BEHANDLING
-- =============================================================================
-- Saker som kan brukes for å teste automatiske prosesser

-- Standard saker som kan behandles automatisk
INSERT INTO sak (bruker_id, type, status, beskrivelse, opprettet_tid, sist_endret) VALUES 
(1, 'DAGPENGER', 'MOTTATT', 'Standard søknad om dagpenger - automatisk behandling', DATEADD('DAY', -8, CURRENT_TIMESTAMP), DATEADD('DAY', -8, CURRENT_TIMESTAMP)),
(3, 'BARNETRYGD', 'MOTTATT', 'Standard barnetrygd søknad', DATEADD('DAY', -5, CURRENT_TIMESTAMP), DATEADD('DAY', -5, CURRENT_TIMESTAMP));

-- Komplekse saker som trenger manuell behandling
INSERT INTO sak (bruker_id, type, status, beskrivelse, opprettet_tid, sist_endret) VALUES 
(2, 'SYKEPENGER', 'UNDER_BEHANDLING', 'Kompleks sak med flere diagnoser', DATEADD('DAY', -20, CURRENT_TIMESTAMP), DATEADD('DAY', -15, CURRENT_TIMESTAMP)),
(4, 'AAP', 'VENTER_DOKUMENTASJON', 'Kompleks AAP-sak - venter på spesialistuttalelse', DATEADD('DAY', -30, CURRENT_TIMESTAMP), DATEADD('DAY', -25, CURRENT_TIMESTAMP));

-- =============================================================================
-- SYNC STATUS TABELL FOR INTEGRASJONER
-- =============================================================================
-- Oppretter SYNC_STATUS tabell for database polling integrasjoner
CREATE TABLE IF NOT EXISTS sync_status (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sync_type VARCHAR(50) NOT NULL,
    last_processed TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_sync_type (sync_type)
);

-- Initialdatapunkt for synkroniseringsoperasjoner
INSERT INTO sync_status (sync_type, last_processed) VALUES 
('CASE_SYNC', DATEADD('DAY', -1, CURRENT_TIMESTAMP)),
('USER_SYNC', DATEADD('DAY', -1, CURRENT_TIMESTAMP)),
('PAYMENT_SYNC', DATEADD('DAY', -1, CURRENT_TIMESTAMP));

-- =============================================================================
-- KOMMENTARER FOR TESTING
-- =============================================================================
-- Dette gir oss testdata for:
--  5 brukere med ulike profiler
--  12 saker som dekker alle sakstyper
--  Saker i alle statuser (fra MOTTATT til AVSLUTTET)
--  Standard saker for automatisk behandling
--  Komplekse saker for manuell behandling
--  Eldre saker for testing av oppfølging og SLA
--  Sync status tabell for integrasjonspoller
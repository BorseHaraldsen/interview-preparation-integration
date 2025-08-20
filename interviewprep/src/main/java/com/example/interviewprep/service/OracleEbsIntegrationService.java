package com.example.interviewprep.service;

import com.example.interviewprep.models.Bruker;
import com.example.interviewprep.models.Sak;
import com.example.interviewprep.models.SaksStatus;
import com.example.interviewprep.repository.BrukerRepository;
import com.example.interviewprep.repository.SakRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Oracle E-Business Suite integration service.
 * 
 * Simulates integration with legacy Oracle EBS system that NAV is migrating from.
 * Demonstrates enterprise patterns for legacy system integration including:
 * - Batch data synchronization
 * - Two-way data mapping and transformation
 * - Change tracking and delta processing
 * - Error handling and retry mechanisms
 * - Transaction coordination between systems
 */
@Service
public class OracleEbsIntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(OracleEbsIntegrationService.class);

    private final RestTemplate restTemplate;
    private final BrukerRepository brukerRepository;
    private final SakRepository sakRepository;

    @Value("${oracle.ebs.base-url:http://legacy-oracle-ebs:8080}")
    private String oracleEbsBaseUrl;

    @Value("${oracle.ebs.username:nav_integration}")
    private String oracleUsername;

    @Value("${oracle.ebs.sync.batch-size:100}")
    private int batchSize;

    @Autowired
    public OracleEbsIntegrationService(RestTemplate restTemplate, 
                                     BrukerRepository brukerRepository,
                                     SakRepository sakRepository) {
        this.restTemplate = restTemplate;
        this.brukerRepository = brukerRepository;
        this.sakRepository = sakRepository;
    }

    /**
     * Synchronizes user data from Oracle EBS to modern platform.
     * Implements delta synchronization to minimize data transfer.
     */
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public void synchronizeUsersFromEbs() {
        logger.info("Starting user synchronization from Oracle EBS");
        
        try {
            LocalDateTime lastSyncTime = getLastSynchronizationTime("USER_SYNC");
            
            // Fetch updated users from Oracle EBS
            List<EbsUserRecord> ebsUsers = fetchUpdatedUsersFromEbs(lastSyncTime);
            logger.info("Retrieved {} updated users from Oracle EBS", ebsUsers.size());

            int processedCount = 0;
            for (EbsUserRecord ebsUser : ebsUsers) {
                try {
                    processUserUpdate(ebsUser);
                    processedCount++;
                } catch (Exception e) {
                    logger.error("Failed to process user update for EBS ID: {}", 
                               ebsUser.getEbsUserId(), e);
                }
            }

            updateLastSynchronizationTime("USER_SYNC", LocalDateTime.now());
            logger.info("User synchronization completed. Processed: {}/{}", 
                       processedCount, ebsUsers.size());

        } catch (Exception e) {
            logger.error("User synchronization from Oracle EBS failed", e);
            throw e;
        }
    }

    /**
     * Synchronizes case data from Oracle EBS workflow system.
     * Maps Oracle EBS workflow statuses to modern case management.
     */
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public void synchronizeCasesFromEbs() {
        logger.info("Starting case synchronization from Oracle EBS");
        
        try {
            LocalDateTime lastSyncTime = getLastSynchronizationTime("CASE_SYNC");
            
            // Fetch updated cases from Oracle EBS workflow
            List<EbsCaseRecord> ebsCases = fetchUpdatedCasesFromEbs(lastSyncTime);
            logger.info("Retrieved {} updated cases from Oracle EBS", ebsCases.size());

            int processedCount = 0;
            for (EbsCaseRecord ebsCase : ebsCases) {
                try {
                    processCaseUpdate(ebsCase);
                    processedCount++;
                } catch (Exception e) {
                    logger.error("Failed to process case update for EBS Case ID: {}", 
                               ebsCase.getEbsCaseId(), e);
                }
            }

            updateLastSynchronizationTime("CASE_SYNC", LocalDateTime.now());
            logger.info("Case synchronization completed. Processed: {}/{}", 
                       processedCount, ebsCases.size());

        } catch (Exception e) {
            logger.error("Case synchronization from Oracle EBS failed", e);
            throw e;
        }
    }

    /**
     * Pushes case status updates back to Oracle EBS.
     * Ensures bidirectional synchronization for legacy system compatibility.
     */
    public void pushCaseStatusToEbs(Long caseId, SaksStatus newStatus) {
        logger.info("Pushing case status update to Oracle EBS: Case={}, Status={}", 
                   caseId, newStatus);
        
        try {
            Optional<Sak> sakOpt = sakRepository.findById(caseId);
            if (sakOpt.isEmpty()) {
                logger.warn("Case not found for EBS update: {}", caseId);
                return;
            }

            Sak sak = sakOpt.get();
            EbsStatusUpdateRequest request = mapToEbsStatusUpdate(sak, newStatus);
            
            HttpHeaders headers = createEbsApiHeaders();
            HttpEntity<EbsStatusUpdateRequest> entity = new HttpEntity<>(request, headers);
            
            String url = oracleEbsBaseUrl + "/api/workflow/cases/{caseId}/status";
            ResponseEntity<EbsResponse> response = restTemplate.exchange(
                url, HttpMethod.PUT, entity, EbsResponse.class, request.getEbsCaseId()
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Successfully updated case status in Oracle EBS: {}", caseId);
            } else {
                logger.warn("Oracle EBS returned non-success status: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            logger.error("Failed to update case status in Oracle EBS", e);
            // In production, this would go to a retry queue
            throw e;
        }
    }

    /**
     * Executes master data reconciliation between systems.
     * Identifies and resolves data inconsistencies.
     */
    public void executeMasterDataReconciliation() {
        logger.info("Starting master data reconciliation with Oracle EBS");
        
        try {
            // Compare user counts
            long localUserCount = brukerRepository.count();
            long ebsUserCount = getEbsUserCount();
            
            if (Math.abs(localUserCount - ebsUserCount) > 10) {
                logger.warn("Significant user count discrepancy detected. Local: {}, EBS: {}", 
                           localUserCount, ebsUserCount);
            }

            // Compare case counts by status
            Map<SaksStatus, Long> localCaseCounts = getCaseCountsByStatus();
            Map<String, Long> ebsCaseCounts = getEbsCaseCountsByStatus();
            
            reconcileCaseCounts(localCaseCounts, ebsCaseCounts);
            
            logger.info("Master data reconciliation completed");

        } catch (Exception e) {
            logger.error("Master data reconciliation failed", e);
            throw e;
        }
    }

    // Private helper methods

    private List<EbsUserRecord> fetchUpdatedUsersFromEbs(LocalDateTime lastSyncTime) {
        // Simulate fetching from Oracle EBS REST API
        logger.debug("Fetching users updated since: {}", lastSyncTime);
        
        // In real implementation, this would call Oracle EBS APIs
        // For simulation, return mock data
        return createMockEbsUsers();
    }

    private List<EbsCaseRecord> fetchUpdatedCasesFromEbs(LocalDateTime lastSyncTime) {
        // Simulate fetching from Oracle EBS Workflow APIs
        logger.debug("Fetching cases updated since: {}", lastSyncTime);
        
        // In real implementation, this would call Oracle EBS Workflow APIs
        return createMockEbsCases();
    }

    private void processUserUpdate(EbsUserRecord ebsUser) {
        logger.debug("Processing user update from EBS: {}", ebsUser.getEbsUserId());
        
        Optional<Bruker> existingUser = brukerRepository.findByFodselsnummer(ebsUser.getNationalId());
        
        if (existingUser.isPresent()) {
            // Update existing user
            Bruker bruker = existingUser.get();
            if (hasUserDataChanged(bruker, ebsUser)) {
                updateUserFromEbs(bruker, ebsUser);
                brukerRepository.save(bruker);
                logger.info("Updated user from EBS: {}", bruker.getId());
            }
        } else {
            // Create new user
            Bruker newBruker = createUserFromEbs(ebsUser);
            brukerRepository.save(newBruker);
            logger.info("Created new user from EBS: {}", newBruker.getId());
        }
    }

    private void processCaseUpdate(EbsCaseRecord ebsCase) {
        logger.debug("Processing case update from EBS: {}", ebsCase.getEbsCaseId());
        
        // Map EBS case to local case
        // This would involve complex business logic in production
        
        Optional<Bruker> brukerOpt = brukerRepository.findByFodselsnummer(ebsCase.getNationalId());
        if (brukerOpt.isEmpty()) {
            logger.warn("User not found for EBS case: {}", ebsCase.getEbsCaseId());
            return;
        }

        // Process case mapping and status transformation
        SaksStatus mappedStatus = mapEbsStatusToLocal(ebsCase.getEbsStatus());
        logger.debug("Mapped EBS status '{}' to local status '{}'", 
                    ebsCase.getEbsStatus(), mappedStatus);
    }

    private HttpHeaders createEbsApiHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + Base64.getEncoder()
                   .encodeToString((oracleUsername + ":password").getBytes()));
        headers.set("X-Client-ID", "NAV-INTEGRATION-PLATFORM");
        return headers;
    }

    private LocalDateTime getLastSynchronizationTime(String syncType) {
        // In production, this would be stored in database
        return LocalDateTime.now().minusHours(1);
    }

    private void updateLastSynchronizationTime(String syncType, LocalDateTime time) {
        // In production, this would update database record
        logger.debug("Updated last sync time for {}: {}", syncType, time);
    }

    // Mock data creation methods for demonstration
    
    private List<EbsUserRecord> createMockEbsUsers() {
        List<EbsUserRecord> users = new ArrayList<>();
        users.add(new EbsUserRecord("EBS001", "12345678901", "Ola Nordmann", "Storgata 1"));
        users.add(new EbsUserRecord("EBS002", "98765432109", "Kari Hansen", "Bjørnstadveien 12"));
        return users;
    }

    private List<EbsCaseRecord> createMockEbsCases() {
        List<EbsCaseRecord> cases = new ArrayList<>();
        cases.add(new EbsCaseRecord("CASE001", "12345678901", "DAGPENGER", "APPROVED", "Benefit approved"));
        cases.add(new EbsCaseRecord("CASE002", "98765432109", "SYKEPENGER", "PENDING", "Medical review pending"));
        return cases;
    }

    private boolean hasUserDataChanged(Bruker bruker, EbsUserRecord ebsUser) {
        return !bruker.getNavn().equals(ebsUser.getFullName()) ||
               !bruker.getAdresse().equals(ebsUser.getAddress());
    }

    private void updateUserFromEbs(Bruker bruker, EbsUserRecord ebsUser) {
        bruker.setNavn(ebsUser.getFullName());
        bruker.setAdresse(ebsUser.getAddress());
    }

    private Bruker createUserFromEbs(EbsUserRecord ebsUser) {
        return new Bruker(ebsUser.getNationalId(), ebsUser.getFullName(), ebsUser.getAddress());
    }

    private SaksStatus mapEbsStatusToLocal(String ebsStatus) {
        return switch (ebsStatus.toUpperCase()) {
            case "SUBMITTED" -> SaksStatus.MOTTATT;
            case "IN_REVIEW" -> SaksStatus.UNDER_BEHANDLING;
            case "APPROVED" -> SaksStatus.VEDTAK_FATTET;
            case "PAID" -> SaksStatus.UTBETALT;
            case "REJECTED" -> SaksStatus.AVVIST;
            case "CLOSED" -> SaksStatus.AVSLUTTET;
            case "PENDING_DOCS" -> SaksStatus.VENTER_DOKUMENTASJON;
            default -> SaksStatus.MOTTATT;
        };
    }

    private EbsStatusUpdateRequest mapToEbsStatusUpdate(Sak sak, SaksStatus newStatus) {
        String ebsStatus = mapLocalStatusToEbs(newStatus);
        return new EbsStatusUpdateRequest("CASE" + sak.getId(), ebsStatus, 
                                        "Updated from NAV integration platform");
    }

    private String mapLocalStatusToEbs(SaksStatus status) {
        return switch (status) {
            case MOTTATT -> "SUBMITTED";
            case UNDER_BEHANDLING -> "IN_REVIEW";
            case VEDTAK_FATTET -> "APPROVED";
            case UTBETALT -> "PAID";
            case AVVIST -> "REJECTED";
            case AVSLUTTET -> "CLOSED";
            case VENTER_DOKUMENTASJON -> "PENDING_DOCS";
        };
    }

    private long getEbsUserCount() {
        // Simulate EBS API call
        return 150;
    }

    private Map<SaksStatus, Long> getCaseCountsByStatus() {
        // In production, this would be a repository query
        Map<SaksStatus, Long> counts = new HashMap<>();
        counts.put(SaksStatus.MOTTATT, 25L);
        counts.put(SaksStatus.UNDER_BEHANDLING, 15L);
        counts.put(SaksStatus.VEDTAK_FATTET, 30L);
        return counts;
    }

    private Map<String, Long> getEbsCaseCountsByStatus() {
        // Simulate EBS API call
        Map<String, Long> counts = new HashMap<>();
        counts.put("SUBMITTED", 25L);
        counts.put("IN_REVIEW", 15L);
        counts.put("APPROVED", 30L);
        return counts;
    }

    private void reconcileCaseCounts(Map<SaksStatus, Long> localCounts, 
                                   Map<String, Long> ebsCounts) {
        logger.info("Reconciling case counts between systems");
        
        for (Map.Entry<SaksStatus, Long> entry : localCounts.entrySet()) {
            String ebsStatus = mapLocalStatusToEbs(entry.getKey());
            Long ebsCount = ebsCounts.get(ebsStatus);
            
            if (ebsCount != null && !entry.getValue().equals(ebsCount)) {
                logger.warn("Case count mismatch for status {}: Local={}, EBS={}", 
                           entry.getKey(), entry.getValue(), ebsCount);
            }
        }
    }

    // DTOs for EBS integration

    private static class EbsUserRecord {
        private final String ebsUserId;
        private final String nationalId;
        private final String fullName;
        private final String address;

        public EbsUserRecord(String ebsUserId, String nationalId, String fullName, String address) {
            this.ebsUserId = ebsUserId;
            this.nationalId = nationalId;
            this.fullName = fullName;
            this.address = address;
        }

        public String getEbsUserId() { return ebsUserId; }
        public String getNationalId() { return nationalId; }
        public String getFullName() { return fullName; }
        public String getAddress() { return address; }
    }

    private static class EbsCaseRecord {
        private final String ebsCaseId;
        private final String nationalId;
        private final String caseType;
        private final String ebsStatus;
        private final String description;

        public EbsCaseRecord(String ebsCaseId, String nationalId, String caseType, 
                           String ebsStatus, String description) {
            this.ebsCaseId = ebsCaseId;
            this.nationalId = nationalId;
            this.caseType = caseType;
            this.ebsStatus = ebsStatus;
            this.description = description;
        }

        public String getEbsCaseId() { return ebsCaseId; }
        public String getNationalId() { return nationalId; }
        public String getCaseType() { return caseType; }
        public String getEbsStatus() { return ebsStatus; }
        public String getDescription() { return description; }
    }

    private static class EbsStatusUpdateRequest {
        private final String ebsCaseId;
        private final String newStatus;
        private final String comment;

        public EbsStatusUpdateRequest(String ebsCaseId, String newStatus, String comment) {
            this.ebsCaseId = ebsCaseId;
            this.newStatus = newStatus;
            this.comment = comment;
        }

        public String getEbsCaseId() { return ebsCaseId; }
        public String getNewStatus() { return newStatus; }
        public String getComment() { return comment; }
    }

    private static class EbsResponse {
        private boolean success;
        private String message;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
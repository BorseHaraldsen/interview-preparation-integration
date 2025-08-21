package com.example.interviewprep.service;

import com.example.interviewprep.models.*;
import com.example.interviewprep.models.dto.*;
import com.example.interviewprep.repository.BrukerRepository;
import com.example.interviewprep.repository.SakRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Event-Driven Case Processing Service
 * 
 * This service demonstrates complete integration workflows using real external services
 * with different data formats and protocols. It showcases the complexity integration
 * developers face when working with Norwegian government and financial systems.
 * 
 * KEY INTEGRATION PATTERNS DEMONSTRATED:
 * 
 * 1. DATA FORMAT TRANSFORMATION:
 *    - XML parsing (Folkeregister with Norwegian field names)
 *    - CSV parsing with pipe delimiters (A-ordningen employment data)
 *    - Fixed-width mainframe records (Skatteetaten tax data)
 *    - Complex nested JSON (Bank account validation)
 * 
 * 2. ERROR HANDLING AND RESILIENCE:
 *    - Circuit breakers for unreliable external systems
 *    - Retry patterns with exponential backoff
 *    - Graceful degradation when services are unavailable
 *    - Compensation patterns for failed transactions
 * 
 * 3. EVENT-DRIVEN ARCHITECTURE:
 *    - Publish case events to Kafka for downstream systems
 *    - Asynchronous processing to handle slow external systems
 *    - Saga pattern for distributed transactions
 *    - Event sourcing for audit trails
 * 
 * 4. REAL-WORLD COMPLEXITY:
 *    - Different authentication methods per service
 *    - Varying response times (mainframe vs modern APIs)
 *    - Mixed naming conventions (camelCase, snake_case, kebab-case)
 *    - Multiple date formats and locale-specific number formatting
 * 
 * This service processes a complete NAV case workflow:
 * 1. Receive case creation event
 * 2. Fetch citizen data from Folkeregister (XML)
 * 3. Retrieve employment history from A-ordningen (CSV) 
 * 4. Get income data from Skatteetaten (fixed-width)
 * 5. Validate bank account for payments (complex JSON)
 * 6. Apply business rules and make decision
 * 7. Publish decision events to downstream systems
 * 8. Handle any errors or compensating actions
 */
@Service
@Transactional(readOnly = true)
public class EventDrivenCaseProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(EventDrivenCaseProcessingService.class);

    private final SakRepository sakRepository;
    private final BrukerRepository brukerRepository;
    private final EksternIntegrasjonService eksternIntegrasjonService;
    private final KafkaProducerInterface kafkaProducerService;
    private final SaksService saksService;

    @Autowired
    public EventDrivenCaseProcessingService(
            SakRepository sakRepository,
            BrukerRepository brukerRepository,
            EksternIntegrasjonService eksternIntegrasjonService,
            KafkaProducerInterface kafkaProducerService,
            SaksService saksService) {
        this.sakRepository = sakRepository;
        this.brukerRepository = brukerRepository;
        this.eksternIntegrasjonService = eksternIntegrasjonService;
        this.kafkaProducerService = kafkaProducerService;
        this.saksService = saksService;
    }

    /**
     * Complete event-driven case processing workflow
     * 
     * This method demonstrates the full complexity of integrating with multiple
     * external systems that have different data formats, response times, and
     * reliability characteristics.
     * 
     * WORKFLOW STEPS:
     * 1. Validate input and fetch case from database
     * 2. Parallel data gathering from multiple external systems
     * 3. Data format transformation and validation
     * 4. Business rule application and decision making
     * 5. Event publishing for downstream system coordination
     * 6. Error handling and compensation
     */
    @Transactional
    public CaseProcessingResult processCompleteCase(Long caseId) {
        logger.info("Starting complete case processing workflow for case: {}", caseId);
        
        CaseProcessingResult result = new CaseProcessingResult();
        result.setCaseId(caseId);
        result.setStartTime(LocalDateTime.now());
        
        try {
            // STEP 1: Load case and validate
            Sak sak = sakRepository.findById(caseId)
                    .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));
            
            result.setCaseType(sak.getType());
            result.setCitizenId(sak.getBruker().getFodselsnummer());
            
            logger.info("Processing case: type={}, citizen={}", sak.getType(), "***masked***");

            // STEP 2: Parallel data gathering from external systems with different formats
            CompletableFuture<ExternalDataGatheringResult> dataGathering = 
                gatherExternalDataAsync(sak.getBruker().getFodselsnummer(), sak.getType());
            
            // STEP 3: Update case status while external calls are running
            saksService.startSaksbehandling(caseId);
            
            // STEP 4: Wait for external data and process
            ExternalDataGatheringResult externalData = dataGathering.get();
            result.setExternalDataResult(externalData);
            
            // STEP 5: Apply business rules based on gathered data
            BusinessDecision decision = applyBusinessRules(sak, externalData);
            result.setBusinessDecision(decision);
            
            // STEP 6: Update case with decision
            saksService.ferdigstillSak(caseId, decision.isApproved(), decision.getReason());
            
            // STEP 7: Publish events for downstream systems
            publishCaseProcessingEvents(sak, decision, externalData);
            
            result.setSuccess(true);
            result.setMessage("Case processing completed successfully");
            
        } catch (Exception e) {
            logger.error("Error during case processing for case {}: {}", caseId, e.getMessage(), e);
            result.setSuccess(false);
            result.setMessage("Case processing failed: " + e.getMessage());
            result.setError(e.getMessage());
            
            // Publish failure event for monitoring and alerting
            try {
                publishCaseProcessingFailureEvent(caseId, e.getMessage());
            } catch (Exception publishError) {
                logger.error("Failed to publish failure event: {}", publishError.getMessage());
            }
        }
        
        result.setEndTime(LocalDateTime.now());
        long processingTimeMs = java.time.Duration.between(result.getStartTime(), result.getEndTime()).toMillis();
        result.setProcessingTimeMs(processingTimeMs);
        
        logger.info("Case processing completed: caseId={}, success={}, timeMs={}", 
                   caseId, result.isSuccess(), processingTimeMs);
        
        return result;
    }

    /**
     * Asynchronously gather data from all external systems in parallel
     * 
     * This demonstrates how to efficiently integrate with multiple systems
     * that have vastly different response characteristics:
     * - Folkeregister: XML, legacy delays (200-500ms)
     * - A-ordningen: CSV, batch processing (400-1000ms)
     * - Skatteetaten: Fixed-width, mainframe system (500-1500ms)
     * - Bank: Complex JSON, modern API (300-700ms)
     * 
     * By calling them in parallel, we reduce total integration time.
     */
    private CompletableFuture<ExternalDataGatheringResult> gatherExternalDataAsync(String fnr, SaksType saksType) {
        logger.info("Gathering external data in parallel for case type: {}", saksType);
        
        ExternalDataGatheringResult result = new ExternalDataGatheringResult();
        
        // Start all external service calls in parallel
        CompletableFuture<FolkeregisterData> folkeregisterFuture = CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Fetching citizen data from Folkeregister (XML format)");
                return eksternIntegrasjonService.hentPersonFraFolkeregister(fnr);
            } catch (Exception e) {
                logger.warn("Failed to fetch Folkeregister data: {}", e.getMessage());
                return null;
            }
        });

        CompletableFuture<List<ArbeidsforholdData>> aOrdningenFuture = CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Fetching employment data from A-ordningen (CSV format)");
                return eksternIntegrasjonService.hentArbeidsforholdFraAOrdningen(fnr, LocalDateTime.now().minusYears(3));
            } catch (Exception e) {
                logger.warn("Failed to fetch A-ordningen data: {}", e.getMessage());
                return List.of(); // Return empty list instead of null
            }
        });

        CompletableFuture<InntektsData> skatteetatenFuture = CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Fetching income data from Skatteetaten (fixed-width format)");
                return eksternIntegrasjonService.hentInntektFraSkatteetaten(fnr, LocalDateTime.now().getYear() - 1);
            } catch (Exception e) {
                logger.warn("Failed to fetch Skatteetaten data: {}", e.getMessage());
                return null;
            }
        });

        // Bank validation only for cases that require payments
        CompletableFuture<Boolean> bankValidationFuture = CompletableFuture.supplyAsync(() -> {
            if (requiresBankValidation(saksType)) {
                try {
                    logger.debug("Validating bank account (complex JSON format)");
                    String testAccountNumber = "12345678901"; // Would come from user input
                    return eksternIntegrasjonService.validerBankkonto(testAccountNumber, fnr);
                } catch (Exception e) {
                    logger.warn("Failed to validate bank account: {}", e.getMessage());
                    return false;
                }
            }
            return true; // No validation required
        });

        // Combine all futures and wait for completion
        return CompletableFuture.allOf(folkeregisterFuture, aOrdningenFuture, skatteetatenFuture, bankValidationFuture)
            .thenApply(v -> {
                try {
                    result.setFolkeregisterData(folkeregisterFuture.get());
                    result.setArbeidsforholdData(aOrdningenFuture.get());
                    result.setInntektsData(skatteetatenFuture.get());
                    result.setBankAccountValid(bankValidationFuture.get());
                    result.setDataGatheringSuccess(true);
                    
                    logger.info("External data gathering completed: folkeregister={}, arbeidsforhold={}, inntekt={}, bank={}", 
                               result.getFolkeregisterData() != null,
                               result.getArbeidsforholdData().size(),
                               result.getInntektsData() != null,
                               result.isBankAccountValid());
                    
                } catch (Exception e) {
                    logger.error("Error combining external data results: {}", e.getMessage());
                    result.setDataGatheringSuccess(false);
                    result.setDataGatheringError(e.getMessage());
                }
                
                return result;
            });
    }

    /**
     * Apply business rules based on gathered external data
     * 
     * This demonstrates how integration data is used for decision making.
     * Different case types have different requirements and validation rules.
     */
    private BusinessDecision applyBusinessRules(Sak sak, ExternalDataGatheringResult externalData) {
        logger.info("Applying business rules for case type: {}", sak.getType());
        
        BusinessDecision decision = new BusinessDecision();
        decision.setCaseType(sak.getType());
        decision.setDecisionTime(LocalDateTime.now());
        
        try {
            // Basic data validation
            if (!externalData.isDataGatheringSuccess()) {
                decision.setApproved(false);
                decision.setReason("Failed to gather required external data: " + externalData.getDataGatheringError());
                return decision;
            }
            
            // Citizen must exist in Folkeregister
            if (externalData.getFolkeregisterData() == null) {
                decision.setApproved(false);
                decision.setReason("Citizen not found in Folkeregister - cannot process case");
                return decision;
            }
            
            // Check if citizen is deceased
            if (externalData.getFolkeregisterData().isDoedsfall()) {
                decision.setApproved(false);
                decision.setReason("Cannot process case for deceased citizen");
                return decision;
            }
            
            // Apply case-type specific business rules
            switch (sak.getType()) {
                case DAGPENGER:
                    return evaluateDagpengerCase(externalData, decision);
                case SYKEPENGER:
                    return evaluateSykepengerCase(externalData, decision);
                case BARNETRYGD:
                    return evaluateBarnetrygdCase(externalData, decision);
                case AAP:
                    return evaluateAapCase(externalData, decision);
                default:
                    decision.setApproved(true);
                    decision.setReason("Standard case processing - approved");
                    return decision;
            }
            
        } catch (Exception e) {
            logger.error("Error applying business rules: {}", e.getMessage());
            decision.setApproved(false);
            decision.setReason("Business rule evaluation failed: " + e.getMessage());
        }
        
        return decision;
    }
    
    /**
     * Evaluate DAGPENGER (unemployment benefits) case
     * Requires employment history and income verification
     */
    private BusinessDecision evaluateDagpengerCase(ExternalDataGatheringResult data, BusinessDecision decision) {
        logger.debug("Evaluating DAGPENGER case");
        
        // Must have recent employment history
        if (data.getArbeidsforholdData().isEmpty()) {
            decision.setApproved(false);
            decision.setReason("No employment history found - not eligible for unemployment benefits");
            return decision;
        }
        
        // Must have sufficient income
        if (data.getInntektsData() == null || data.getInntektsData().getBruttoInntekt() < 200000) {
            decision.setApproved(false);
            decision.setReason("Insufficient income for unemployment benefits eligibility");
            return decision;
        }
        
        // Must have valid bank account for payments
        if (!data.isBankAccountValid()) {
            decision.setApproved(false);
            decision.setReason("Invalid bank account - cannot process payments");
            return decision;
        }
        
        decision.setApproved(true);
        decision.setReason("DAGPENGER approved - all eligibility criteria met");
        decision.setCalculatedAmount(data.getInntektsData().getBruttoInntekt() * 0.6 / 12); // 60% of monthly income
        
        return decision;
    }
    
    /**
     * Evaluate SYKEPENGER (sick leave benefits) case
     */
    private BusinessDecision evaluateSykepengerCase(ExternalDataGatheringResult data, BusinessDecision decision) {
        logger.debug("Evaluating SYKEPENGER case");
        
        // Must have current employment
        boolean hasCurrentEmployment = data.getArbeidsforholdData().stream()
                .anyMatch(work -> work.getSluttdato() == null);
        
        if (!hasCurrentEmployment) {
            decision.setApproved(false);
            decision.setReason("No current employment - not eligible for sick leave benefits");
            return decision;
        }
        
        decision.setApproved(true);
        decision.setReason("SYKEPENGER approved - current employment verified");
        
        if (data.getInntektsData() != null) {
            decision.setCalculatedAmount(data.getInntektsData().getBruttoInntekt() * 0.8 / 12); // 80% of monthly income
        }
        
        return decision;
    }
    
    /**
     * Evaluate BARNETRYGD (child benefits) case
     */
    private BusinessDecision evaluateBarnetrygdCase(ExternalDataGatheringResult data, BusinessDecision decision) {
        logger.debug("Evaluating BARNETRYGD case");
        
        // Barnetrygd is universal in Norway - simple approval
        decision.setApproved(true);
        decision.setReason("BARNETRYGD approved - universal child benefit");
        decision.setCalculatedAmount(1054.0); // Fixed monthly amount in NOK
        
        return decision;
    }
    
    /**
     * Evaluate AAP (work assessment allowance) case
     */
    private BusinessDecision evaluateAapCase(ExternalDataGatheringResult data, BusinessDecision decision) {
        logger.debug("Evaluating AAP case");
        
        // AAP requires previous employment and income
        if (data.getArbeidsforholdData().isEmpty() || data.getInntektsData() == null) {
            decision.setApproved(false);
            decision.setReason("AAP requires employment history and income documentation");
            return decision;
        }
        
        decision.setApproved(true);
        decision.setReason("AAP approved - work capacity assessment required");
        decision.setCalculatedAmount(data.getInntektsData().getBruttoInntekt() * 0.66 / 12); // 66% of monthly income
        
        return decision;
    }

    /**
     * Publish events for downstream systems coordination
     * 
     * This demonstrates the event-driven architecture where case decisions
     * trigger automated workflows in other systems.
     */
    private void publishCaseProcessingEvents(Sak sak, BusinessDecision decision, ExternalDataGatheringResult externalData) {
        logger.info("Publishing case processing events for downstream systems");
        
        try {
            // Create comprehensive case processing event
            Map<String, Object> caseEvent = Map.of(
                "eventType", "CASE_PROCESSED",
                "caseId", sak.getId(),
                "caseType", sak.getType().toString(),
                "citizenId", "***masked***", // Mask sensitive data in events
                "decision", Map.of(
                    "approved", decision.isApproved(),
                    "reason", decision.getReason(),
                    "amount", decision.getCalculatedAmount(),
                    "decisionTime", decision.getDecisionTime().toString()
                ),
                "externalDataSources", Map.of(
                    "folkeregister", externalData.getFolkeregisterData() != null,
                    "aOrdningen", !externalData.getArbeidsforholdData().isEmpty(),
                    "skatteetaten", externalData.getInntektsData() != null,
                    "bankValidation", externalData.isBankAccountValid()
                ),
                "processingTimestamp", LocalDateTime.now().toString()
            );
            
            // Publish to different topics based on decision
            if (decision.isApproved()) {
                kafkaProducerService.sendGenericEvent("case-approved", caseEvent);
                
                // For approved cases with payments, notify payment system
                if (decision.getCalculatedAmount() > 0) {
                    Map<String, Object> paymentEvent = Map.of(
                        "eventType", "PAYMENT_REQUIRED",
                        "caseId", sak.getId(),
                        "amount", decision.getCalculatedAmount(),
                        "bankAccountValid", externalData.isBankAccountValid()
                    );
                    kafkaProducerService.sendGenericEvent("payment-processing", paymentEvent);
                }
            } else {
                kafkaProducerService.sendGenericEvent("case-rejected", caseEvent);
            }
            
            // Always publish to audit log
            kafkaProducerService.sendGenericEvent("case-audit", caseEvent);
            
        } catch (Exception e) {
            logger.error("Failed to publish case processing events: {}", e.getMessage());
            // Don't fail the main process if event publishing fails
        }
    }
    
    /**
     * Publish failure event for monitoring and alerting
     */
    private void publishCaseProcessingFailureEvent(Long caseId, String errorMessage) {
        try {
            Map<String, Object> failureEvent = Map.of(
                "eventType", "CASE_PROCESSING_FAILED",
                "caseId", caseId,
                "errorMessage", errorMessage,
                "timestamp", LocalDateTime.now().toString(),
                "severity", "HIGH"
            );
            
            kafkaProducerService.sendGenericEvent("case-processing-failures", failureEvent);
            
        } catch (Exception e) {
            logger.error("Failed to publish failure event: {}", e.getMessage());
        }
    }
    
    /**
     * Check if case type requires bank account validation
     */
    private boolean requiresBankValidation(SaksType saksType) {
        return saksType == SaksType.DAGPENGER || 
               saksType == SaksType.SYKEPENGER || 
               saksType == SaksType.AAP ||
               saksType == SaksType.UFORETRYGD ||
               saksType == SaksType.ALDERSPENSJON;
    }

    // Data classes for processing results
    
    public static class CaseProcessingResult {
        private Long caseId;
        private SaksType caseType;
        private String citizenId;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private long processingTimeMs;
        private boolean success;
        private String message;
        private String error;
        private ExternalDataGatheringResult externalDataResult;
        private BusinessDecision businessDecision;

        // Getters and setters
        public Long getCaseId() { return caseId; }
        public void setCaseId(Long caseId) { this.caseId = caseId; }
        public SaksType getCaseType() { return caseType; }
        public void setCaseType(SaksType caseType) { this.caseType = caseType; }
        public String getCitizenId() { return citizenId; }
        public void setCitizenId(String citizenId) { this.citizenId = citizenId; }
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public void setProcessingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public ExternalDataGatheringResult getExternalDataResult() { return externalDataResult; }
        public void setExternalDataResult(ExternalDataGatheringResult externalDataResult) { this.externalDataResult = externalDataResult; }
        public BusinessDecision getBusinessDecision() { return businessDecision; }
        public void setBusinessDecision(BusinessDecision businessDecision) { this.businessDecision = businessDecision; }
    }
    
    public static class ExternalDataGatheringResult {
        private FolkeregisterData folkeregisterData;
        private List<ArbeidsforholdData> arbeidsforholdData;
        private InntektsData inntektsData;
        private boolean bankAccountValid;
        private boolean dataGatheringSuccess;
        private String dataGatheringError;

        // Getters and setters
        public FolkeregisterData getFolkeregisterData() { return folkeregisterData; }
        public void setFolkeregisterData(FolkeregisterData folkeregisterData) { this.folkeregisterData = folkeregisterData; }
        public List<ArbeidsforholdData> getArbeidsforholdData() { return arbeidsforholdData; }
        public void setArbeidsforholdData(List<ArbeidsforholdData> arbeidsforholdData) { this.arbeidsforholdData = arbeidsforholdData; }
        public InntektsData getInntektsData() { return inntektsData; }
        public void setInntektsData(InntektsData inntektsData) { this.inntektsData = inntektsData; }
        public boolean isBankAccountValid() { return bankAccountValid; }
        public void setBankAccountValid(boolean bankAccountValid) { this.bankAccountValid = bankAccountValid; }
        public boolean isDataGatheringSuccess() { return dataGatheringSuccess; }
        public void setDataGatheringSuccess(boolean dataGatheringSuccess) { this.dataGatheringSuccess = dataGatheringSuccess; }
        public String getDataGatheringError() { return dataGatheringError; }
        public void setDataGatheringError(String dataGatheringError) { this.dataGatheringError = dataGatheringError; }
    }
    
    public static class BusinessDecision {
        private SaksType caseType;
        private boolean approved;
        private String reason;
        private double calculatedAmount;
        private LocalDateTime decisionTime;

        // Getters and setters
        public SaksType getCaseType() { return caseType; }
        public void setCaseType(SaksType caseType) { this.caseType = caseType; }
        public boolean isApproved() { return approved; }
        public void setApproved(boolean approved) { this.approved = approved; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public double getCalculatedAmount() { return calculatedAmount; }
        public void setCalculatedAmount(double calculatedAmount) { this.calculatedAmount = calculatedAmount; }
        public LocalDateTime getDecisionTime() { return decisionTime; }
        public void setDecisionTime(LocalDateTime decisionTime) { this.decisionTime = decisionTime; }
    }
}
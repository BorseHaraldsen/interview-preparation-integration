package no.nav.bank.controller;

import no.nav.bank.model.BankAccountResponse;
import no.nav.bank.model.BankAccountResponse.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Bank Controller - Returns complex JSON with different field naming conventions
 * 
 * This controller demonstrates banking integration challenges:
 * - Mixed naming conventions (camelCase, snake_case, kebab-case)
 * - ISO 8601 timestamps vs Norwegian date formats  
 * - Complex nested JSON structures
 * - Currency handling with ISO codes
 * - Bank-specific pagination parameters
 * - Account number validation with MOD-11
 */
@RestController
@RequestMapping("/api/banking")
public class BankController {
    
    private static final Pattern NORWEGIAN_ACCOUNT_PATTERN = Pattern.compile("\\d{11}");
    private static final Pattern NATIONAL_ID_PATTERN = Pattern.compile("\\d{11}");

    /**
     * Get account information using complex JSON structure
     * Different pagination parameters: page/size instead of offset/limit
     */
    @GetMapping(value = "/accounts/{accountNumber}", produces = MediaType.APPLICATION_JSON_VALUE)
    public BankAccountResponse getAccountInfo(
            @PathVariable String accountNumber,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) throws InterruptedException {
        
        // Simulate banking API processing delay (realistic for financial systems)
        Thread.sleep(300 + (long)(Math.random() * 400)); // 300-700ms delay
        
        if (!isValidNorwegianAccountNumber(accountNumber)) {
            throw new IllegalArgumentException("Invalid Norwegian account number format");
        }
        
        return buildBankAccountResponse(accountNumber, page, size);
    }

    /**
     * Health check endpoint with banking-specific response format
     */
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object healthCheck() {
        return new Object() {
            public final String service_name = "Banking Integration API";
            public final String status_code = "OPERATIONAL";
            public final Instant timestamp = Instant.now();
            public final String api_version = "v2.1.4";
        };
    }

    /**
     * Build complex bank account response with different data structures
     * Forces integration layer to handle field mapping and transformation
     */
    private BankAccountResponse buildBankAccountResponse(String accountNumber, int page, int size) {
        
        // Generate national ID from account number for demo purposes
        String nationalId = generateNationalIdFromAccount(accountNumber);
        
        // Build complex nested structures with different naming conventions
        AccountHolderInfo accountHolder = createAccountHolder(nationalId);
        BalanceInfo accountBalance = createAccountBalance();
        List<TransactionInfo> recentTransactions = createRecentTransactions(accountNumber, size);
        AccountStatusInfo accountStatus = createAccountStatus();
        ResponseMetadata metadata = createResponseMetadata(page, size, recentTransactions.size());
        
        return new BankAccountResponse(
            accountNumber,
            accountHolder,
            accountBalance,
            recentTransactions,
            accountStatus,
            metadata
        );
    }
    
    private AccountHolderInfo createAccountHolder(String nationalId) {
        // Generate realistic Norwegian data based on national ID
        String[] names = {"Lars Hansen", "Kari Olsen", "Per Andersen", "Anne Larsen", 
                         "Ole Pedersen", "Astrid Nilsen", "Erik Kristiansen", "Liv Jensen"};
        
        int nameIndex = Integer.parseInt(nationalId.substring(0, 1)) % names.length;
        String fullName = names[nameIndex];
        
        // Convert fødselsnummer to ISO date format (yyyy-MM-dd)
        String dateOfBirth = convertFnrToIsoDate(nationalId);
        
        // Create contact details with different structure than NAV format
        ContactDetails.MailingAddress mailingAddress = new ContactDetails.MailingAddress(
            "Storgata " + nationalId.substring(0, 2),
            "01" + nationalId.substring(2, 4),
            "Oslo",
            "NO" // ISO 3166-1 alpha-2 country code
        );
        
        ContactDetails contactDetails = new ContactDetails(
            fullName.toLowerCase().replace(" ", ".") + "@example.no",
            "+47" + nationalId.substring(0, 8),
            mailingAddress
        );
        
        return new AccountHolderInfo(
            "CUST_" + nationalId.substring(0, 6),
            nationalId,
            fullName,
            dateOfBirth,
            contactDetails
        );
    }
    
    private BalanceInfo createAccountBalance() {
        // Generate realistic balance amounts with currency codes
        double availableAmount = 45000 + (Math.random() * 200000);
        double pendingAmount = Math.random() * 5000;
        
        BalanceInfo.MoneyAmount availableBalance = new BalanceInfo.MoneyAmount(availableAmount, "NOK");
        BalanceInfo.MoneyAmount pendingBalance = new BalanceInfo.MoneyAmount(pendingAmount, "NOK");
        
        return new BalanceInfo(availableBalance, pendingBalance, Instant.now().minusSeconds(3600));
    }
    
    private List<TransactionInfo> createRecentTransactions(String accountNumber, int size) {
        List<TransactionInfo> transactions = new ArrayList<>();
        String[] transactionTypes = {"DEBIT", "CREDIT", "TRANSFER"};
        String[] statusCodes = {"COMPLETED", "PENDING", "COMPLETED", "COMPLETED"}; // Most completed
        String[] descriptions = {"Salary payment", "Grocery purchase", "Rent payment", "ATM withdrawal", 
                               "Online transfer", "Card payment", "Direct debit"};
        
        for (int i = 0; i < Math.min(size, 5); i++) {
            String transactionId = "TXN_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            Instant transactionDate = Instant.now().minusSeconds(86400L * i); // Days ago
            
            double amount = 100 + (Math.random() * 5000);
            BalanceInfo.MoneyAmount transactionAmount = new BalanceInfo.MoneyAmount(amount, "NOK");
            
            String transactionType = transactionTypes[i % transactionTypes.length];
            String statusCode = statusCodes[i % statusCodes.length];
            String description = descriptions[i % descriptions.length];
            
            transactions.add(new TransactionInfo(
                transactionId, 
                transactionDate, 
                transactionAmount, 
                transactionType, 
                description, 
                statusCode
            ));
        }
        
        return transactions;
    }
    
    private AccountStatusInfo createAccountStatus() {
        return new AccountStatusInfo(
            "ACTIVE",
            "Account is active and operational",
            true,
            Instant.now().minusSeconds(7776000) // 90 days ago
        );
    }
    
    private ResponseMetadata createResponseMetadata(int page, int size, int returnedRecords) {
        ResponseMetadata.PaginationInfo pagination = new ResponseMetadata.PaginationInfo(
            page,
            size,
            returnedRecords,
            false // No next page for demo
        );
        
        return new ResponseMetadata(
            Instant.now(),
            "RESP_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
            "2.1.4",
            pagination
        );
    }
    
    /**
     * Validate Norwegian account number using MOD-11 algorithm
     * This creates validation complexity that integration must handle
     */
    private boolean isValidNorwegianAccountNumber(String accountNumber) {
        if (!NORWEGIAN_ACCOUNT_PATTERN.matcher(accountNumber).matches()) {
            return false;
        }
        
        // Simplified MOD-11 validation for demo purposes
        // Real implementation would be more complex
        int sum = 0;
        int[] weights = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2, 1};
        
        for (int i = 0; i < 11; i++) {
            sum += Character.getNumericValue(accountNumber.charAt(i)) * weights[i];
        }
        
        return sum % 11 == 0;
    }
    
    private String generateNationalIdFromAccount(String accountNumber) {
        // Generate a valid-looking fødselsnummer based on account number
        // For demo purposes - not a real conversion
        return accountNumber; // Simplified for demo
    }
    
    private String convertFnrToIsoDate(String fødselsnummer) {
        // Convert fødselsnummer to ISO date format (yyyy-MM-dd)
        // This demonstrates date format transformation complexity
        String day = fødselsnummer.substring(0, 2);
        String month = fødselsnummer.substring(2, 4);
        String year = fødselsnummer.substring(4, 6);
        
        // Simplified century calculation
        int yearNum = Integer.parseInt(year);
        String fullYear = (yearNum > 50) ? "19" + year : "20" + year;
        
        return fullYear + "-" + month + "-" + day;
    }
}
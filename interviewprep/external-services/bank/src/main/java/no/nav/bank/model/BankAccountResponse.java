package no.nav.bank.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * Bank API response structure - deliberately different from NAV's internal format
 * Uses banking industry standards and naming conventions that require transformation
 */
public class BankAccountResponse {
    
    @JsonProperty("account-number")
    private String accountNumber; // Uses kebab-case instead of camelCase
    
    @JsonProperty("account_holder")
    private AccountHolderInfo accountHolder; // Uses snake_case  
    
    @JsonProperty("accountBalance")
    private BalanceInfo accountBalance; // Uses camelCase
    
    @JsonProperty("recent_transactions")
    private List<TransactionInfo> recentTransactions;
    
    @JsonProperty("account-status")
    private AccountStatusInfo accountStatus;
    
    @JsonProperty("metadata")
    private ResponseMetadata metadata;

    // Nested account holder structure with international field names
    public static class AccountHolderInfo {
        @JsonProperty("customer_id")
        private String customerId;
        
        @JsonProperty("national_id")
        private String nationalId; // Different from fødselsnummer
        
        @JsonProperty("full_name")
        private String fullName;
        
        @JsonProperty("date_of_birth")
        private String dateOfBirth; // ISO format: yyyy-MM-dd
        
        @JsonProperty("contact-details")
        private ContactDetails contactDetails;

        public static class ContactDetails {
            @JsonProperty("email_address")
            private String emailAddress;
            
            @JsonProperty("phone-number")
            private String phoneNumber;
            
            @JsonProperty("mailing_address")
            private MailingAddress mailingAddress;

            public static class MailingAddress {
                @JsonProperty("street_address")
                private String streetAddress;
                
                @JsonProperty("postal-code")
                private String postalCode;
                
                @JsonProperty("city")
                private String city;
                
                @JsonProperty("country-code")
                private String countryCode; // ISO 3166-1 alpha-2

                // Constructors
                public MailingAddress() {}
                public MailingAddress(String streetAddress, String postalCode, String city, String countryCode) {
                    this.streetAddress = streetAddress;
                    this.postalCode = postalCode;
                    this.city = city;
                    this.countryCode = countryCode;
                }

                // Getters and setters
                public String getStreetAddress() { return streetAddress; }
                public void setStreetAddress(String streetAddress) { this.streetAddress = streetAddress; }
                public String getPostalCode() { return postalCode; }
                public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
                public String getCity() { return city; }
                public void setCity(String city) { this.city = city; }
                public String getCountryCode() { return countryCode; }
                public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
            }

            // Constructors
            public ContactDetails() {}
            public ContactDetails(String emailAddress, String phoneNumber, MailingAddress mailingAddress) {
                this.emailAddress = emailAddress;
                this.phoneNumber = phoneNumber;
                this.mailingAddress = mailingAddress;
            }

            // Getters and setters
            public String getEmailAddress() { return emailAddress; }
            public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }
            public String getPhoneNumber() { return phoneNumber; }
            public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
            public MailingAddress getMailingAddress() { return mailingAddress; }
            public void setMailingAddress(MailingAddress mailingAddress) { this.mailingAddress = mailingAddress; }
        }

        // Constructors
        public AccountHolderInfo() {}
        public AccountHolderInfo(String customerId, String nationalId, String fullName, String dateOfBirth, ContactDetails contactDetails) {
            this.customerId = customerId;
            this.nationalId = nationalId;
            this.fullName = fullName;
            this.dateOfBirth = dateOfBirth;
            this.contactDetails = contactDetails;
        }

        // Getters and setters
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public String getNationalId() { return nationalId; }
        public void setNationalId(String nationalId) { this.nationalId = nationalId; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getDateOfBirth() { return dateOfBirth; }
        public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }
        public ContactDetails getContactDetails() { return contactDetails; }
        public void setContactDetails(ContactDetails contactDetails) { this.contactDetails = contactDetails; }
    }

    // Balance with currency information
    public static class BalanceInfo {
        @JsonProperty("available_balance")
        private MoneyAmount availableBalance;
        
        @JsonProperty("pending-balance")
        private MoneyAmount pendingBalance;
        
        @JsonProperty("last_updated")
        private Instant lastUpdated; // ISO 8601 instant format

        public static class MoneyAmount {
            @JsonProperty("amount")
            private double amount;
            
            @JsonProperty("currency-code")
            private String currencyCode; // ISO 4217 (NOK, USD, EUR)

            public MoneyAmount() {}
            public MoneyAmount(double amount, String currencyCode) {
                this.amount = amount;
                this.currencyCode = currencyCode;
            }

            public double getAmount() { return amount; }
            public void setAmount(double amount) { this.amount = amount; }
            public String getCurrencyCode() { return currencyCode; }
            public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
        }

        // Constructors
        public BalanceInfo() {}
        public BalanceInfo(MoneyAmount availableBalance, MoneyAmount pendingBalance, Instant lastUpdated) {
            this.availableBalance = availableBalance;
            this.pendingBalance = pendingBalance;
            this.lastUpdated = lastUpdated;
        }

        // Getters and setters
        public MoneyAmount getAvailableBalance() { return availableBalance; }
        public void setAvailableBalance(MoneyAmount availableBalance) { this.availableBalance = availableBalance; }
        public MoneyAmount getPendingBalance() { return pendingBalance; }
        public void setPendingBalance(MoneyAmount pendingBalance) { this.pendingBalance = pendingBalance; }
        public Instant getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
    }

    // Transaction information with banking terminology
    public static class TransactionInfo {
        @JsonProperty("transaction-id")
        private String transactionId;
        
        @JsonProperty("transaction_date")
        private Instant transactionDate;
        
        @JsonProperty("transaction-amount")
        private BalanceInfo.MoneyAmount transactionAmount;
        
        @JsonProperty("transaction_type")
        private String transactionType; // DEBIT, CREDIT, TRANSFER
        
        @JsonProperty("description")
        private String description;
        
        @JsonProperty("status-code")
        private String statusCode; // COMPLETED, PENDING, FAILED, REVERSED

        // Constructors
        public TransactionInfo() {}
        public TransactionInfo(String transactionId, Instant transactionDate, BalanceInfo.MoneyAmount transactionAmount, 
                              String transactionType, String description, String statusCode) {
            this.transactionId = transactionId;
            this.transactionDate = transactionDate;
            this.transactionAmount = transactionAmount;
            this.transactionType = transactionType;
            this.description = description;
            this.statusCode = statusCode;
        }

        // Getters and setters
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        public Instant getTransactionDate() { return transactionDate; }
        public void setTransactionDate(Instant transactionDate) { this.transactionDate = transactionDate; }
        public BalanceInfo.MoneyAmount getTransactionAmount() { return transactionAmount; }
        public void setTransactionAmount(BalanceInfo.MoneyAmount transactionAmount) { this.transactionAmount = transactionAmount; }
        public String getTransactionType() { return transactionType; }
        public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getStatusCode() { return statusCode; }
        public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    }

    // Account status with banking terminology
    public static class AccountStatusInfo {
        @JsonProperty("status_code")
        private String statusCode; // ACTIVE, INACTIVE, FROZEN, CLOSED
        
        @JsonProperty("status-description")
        private String statusDescription;
        
        @JsonProperty("is_verified")
        private boolean isVerified;
        
        @JsonProperty("verification-date")
        private Instant verificationDate;

        // Constructors
        public AccountStatusInfo() {}
        public AccountStatusInfo(String statusCode, String statusDescription, boolean isVerified, Instant verificationDate) {
            this.statusCode = statusCode;
            this.statusDescription = statusDescription;
            this.isVerified = isVerified;
            this.verificationDate = verificationDate;
        }

        // Getters and setters
        public String getStatusCode() { return statusCode; }
        public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
        public String getStatusDescription() { return statusDescription; }
        public void setStatusDescription(String statusDescription) { this.statusDescription = statusDescription; }
        public boolean isVerified() { return isVerified; }
        public void setVerified(boolean verified) { isVerified = verified; }
        public Instant getVerificationDate() { return verificationDate; }
        public void setVerificationDate(Instant verificationDate) { this.verificationDate = verificationDate; }
    }

    // Response metadata with different pagination format
    public static class ResponseMetadata {
        @JsonProperty("request_timestamp")
        private Instant requestTimestamp;
        
        @JsonProperty("response-id")
        private String responseId;
        
        @JsonProperty("api_version")
        private String apiVersion;
        
        @JsonProperty("pagination")
        private PaginationInfo pagination;

        public static class PaginationInfo {
            @JsonProperty("page_number")
            private int pageNumber;
            
            @JsonProperty("page-size")
            private int pageSize;
            
            @JsonProperty("total_records")
            private long totalRecords;
            
            @JsonProperty("has-next-page")
            private boolean hasNextPage;

            // Constructors
            public PaginationInfo() {}
            public PaginationInfo(int pageNumber, int pageSize, long totalRecords, boolean hasNextPage) {
                this.pageNumber = pageNumber;
                this.pageSize = pageSize;
                this.totalRecords = totalRecords;
                this.hasNextPage = hasNextPage;
            }

            // Getters and setters
            public int getPageNumber() { return pageNumber; }
            public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }
            public int getPageSize() { return pageSize; }
            public void setPageSize(int pageSize) { this.pageSize = pageSize; }
            public long getTotalRecords() { return totalRecords; }
            public void setTotalRecords(long totalRecords) { this.totalRecords = totalRecords; }
            public boolean isHasNextPage() { return hasNextPage; }
            public void setHasNextPage(boolean hasNextPage) { this.hasNextPage = hasNextPage; }
        }

        // Constructors
        public ResponseMetadata() {}
        public ResponseMetadata(Instant requestTimestamp, String responseId, String apiVersion, PaginationInfo pagination) {
            this.requestTimestamp = requestTimestamp;
            this.responseId = responseId;
            this.apiVersion = apiVersion;
            this.pagination = pagination;
        }

        // Getters and setters
        public Instant getRequestTimestamp() { return requestTimestamp; }
        public void setRequestTimestamp(Instant requestTimestamp) { this.requestTimestamp = requestTimestamp; }
        public String getResponseId() { return responseId; }
        public void setResponseId(String responseId) { this.responseId = responseId; }
        public String getApiVersion() { return apiVersion; }
        public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }
        public PaginationInfo getPagination() { return pagination; }
        public void setPagination(PaginationInfo pagination) { this.pagination = pagination; }
    }

    // Main class constructors and getters/setters
    public BankAccountResponse() {}
    
    public BankAccountResponse(String accountNumber, AccountHolderInfo accountHolder, BalanceInfo accountBalance, 
                              List<TransactionInfo> recentTransactions, AccountStatusInfo accountStatus, 
                              ResponseMetadata metadata) {
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.accountBalance = accountBalance;
        this.recentTransactions = recentTransactions;
        this.accountStatus = accountStatus;
        this.metadata = metadata;
    }

    // Getters and setters
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public AccountHolderInfo getAccountHolder() { return accountHolder; }
    public void setAccountHolder(AccountHolderInfo accountHolder) { this.accountHolder = accountHolder; }
    public BalanceInfo getAccountBalance() { return accountBalance; }
    public void setAccountBalance(BalanceInfo accountBalance) { this.accountBalance = accountBalance; }
    public List<TransactionInfo> getRecentTransactions() { return recentTransactions; }
    public void setRecentTransactions(List<TransactionInfo> recentTransactions) { this.recentTransactions = recentTransactions; }
    public AccountStatusInfo getAccountStatus() { return accountStatus; }
    public void setAccountStatus(AccountStatusInfo accountStatus) { this.accountStatus = accountStatus; }
    public ResponseMetadata getMetadata() { return metadata; }
    public void setMetadata(ResponseMetadata metadata) { this.metadata = metadata; }
}
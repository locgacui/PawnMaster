package com.pawnmaster.api.modules.collateral;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.VerificationDocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checkpoint 2: 2D LTV Matrix Policy & Document Verification Unit Test Suite.
 * Pure in-memory execution (< 200ms).
 */
@DisplayName("Checkpoint 2: 2D LTV Matrix & Document Verification Policy Tests")
public class LtvMatrixPolicyTest {

    private LtvMatrixPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new LtvMatrixPolicy();
    }

    // =========================================================================
    // 1. ALL 48 COMBINATIONS MATRIX ACCURACY (6 Occupations x 8 Categories)
    // =========================================================================

    static Stream<Arguments> provideAll48MatrixCombinations() {
        return Stream.of(
                // 1. OFFICE_WORKER
                Arguments.of(CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.GOLD_JEWELRY, new BigDecimal("0.92")),
                Arguments.of(CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.MOTORBIKE, new BigDecimal("0.90")),
                Arguments.of(CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.CAR, new BigDecimal("0.85")),
                Arguments.of(CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.PHONE, new BigDecimal("0.80")),
                Arguments.of(CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.LAPTOP, new BigDecimal("0.80")),
                Arguments.of(CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.WATCH, new BigDecimal("0.75")),
                Arguments.of(CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.REAL_ESTATE, new BigDecimal("0.75")),
                Arguments.of(CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.OTHER, new BigDecimal("0.70")),

                // 2. FACTORY_WORKER
                Arguments.of(CustomerOccupationGroup.FACTORY_WORKER, CollateralCategory.GOLD_JEWELRY, new BigDecimal("0.90")),
                Arguments.of(CustomerOccupationGroup.FACTORY_WORKER, CollateralCategory.MOTORBIKE, new BigDecimal("0.80")),
                Arguments.of(CustomerOccupationGroup.FACTORY_WORKER, CollateralCategory.CAR, new BigDecimal("0.75")),
                Arguments.of(CustomerOccupationGroup.FACTORY_WORKER, CollateralCategory.PHONE, new BigDecimal("0.75")),
                Arguments.of(CustomerOccupationGroup.FACTORY_WORKER, CollateralCategory.LAPTOP, new BigDecimal("0.75")),
                Arguments.of(CustomerOccupationGroup.FACTORY_WORKER, CollateralCategory.WATCH, new BigDecimal("0.70")),
                Arguments.of(CustomerOccupationGroup.FACTORY_WORKER, CollateralCategory.REAL_ESTATE, new BigDecimal("0.70")),
                Arguments.of(CustomerOccupationGroup.FACTORY_WORKER, CollateralCategory.OTHER, new BigDecimal("0.65")),

                // 3. TECH_DRIVER
                Arguments.of(CustomerOccupationGroup.TECH_DRIVER, CollateralCategory.GOLD_JEWELRY, new BigDecimal("0.88")),
                Arguments.of(CustomerOccupationGroup.TECH_DRIVER, CollateralCategory.MOTORBIKE, new BigDecimal("0.70")),
                Arguments.of(CustomerOccupationGroup.TECH_DRIVER, CollateralCategory.CAR, new BigDecimal("0.70")),
                Arguments.of(CustomerOccupationGroup.TECH_DRIVER, CollateralCategory.PHONE, new BigDecimal("0.70")),
                Arguments.of(CustomerOccupationGroup.TECH_DRIVER, CollateralCategory.LAPTOP, new BigDecimal("0.70")),
                Arguments.of(CustomerOccupationGroup.TECH_DRIVER, CollateralCategory.WATCH, new BigDecimal("0.65")),
                Arguments.of(CustomerOccupationGroup.TECH_DRIVER, CollateralCategory.REAL_ESTATE, new BigDecimal("0.65")),
                Arguments.of(CustomerOccupationGroup.TECH_DRIVER, CollateralCategory.OTHER, new BigDecimal("0.60")),

                // 4. SMALL_MERCHANT
                Arguments.of(CustomerOccupationGroup.SMALL_MERCHANT, CollateralCategory.GOLD_JEWELRY, new BigDecimal("0.90")),
                Arguments.of(CustomerOccupationGroup.SMALL_MERCHANT, CollateralCategory.MOTORBIKE, new BigDecimal("0.80")),
                Arguments.of(CustomerOccupationGroup.SMALL_MERCHANT, CollateralCategory.CAR, new BigDecimal("0.80")),
                Arguments.of(CustomerOccupationGroup.SMALL_MERCHANT, CollateralCategory.PHONE, new BigDecimal("0.75")),
                Arguments.of(CustomerOccupationGroup.SMALL_MERCHANT, CollateralCategory.LAPTOP, new BigDecimal("0.75")),
                Arguments.of(CustomerOccupationGroup.SMALL_MERCHANT, CollateralCategory.WATCH, new BigDecimal("0.75")),
                Arguments.of(CustomerOccupationGroup.SMALL_MERCHANT, CollateralCategory.REAL_ESTATE, new BigDecimal("0.70")),
                Arguments.of(CustomerOccupationGroup.SMALL_MERCHANT, CollateralCategory.OTHER, new BigDecimal("0.65")),

                // 5. ONLINE_SELLER
                Arguments.of(CustomerOccupationGroup.ONLINE_SELLER, CollateralCategory.GOLD_JEWELRY, new BigDecimal("0.88")),
                Arguments.of(CustomerOccupationGroup.ONLINE_SELLER, CollateralCategory.MOTORBIKE, new BigDecimal("0.75")),
                Arguments.of(CustomerOccupationGroup.ONLINE_SELLER, CollateralCategory.CAR, new BigDecimal("0.75")),
                Arguments.of(CustomerOccupationGroup.ONLINE_SELLER, CollateralCategory.PHONE, new BigDecimal("0.70")),
                Arguments.of(CustomerOccupationGroup.ONLINE_SELLER, CollateralCategory.LAPTOP, new BigDecimal("0.70")),
                Arguments.of(CustomerOccupationGroup.ONLINE_SELLER, CollateralCategory.WATCH, new BigDecimal("0.68")),
                Arguments.of(CustomerOccupationGroup.ONLINE_SELLER, CollateralCategory.REAL_ESTATE, new BigDecimal("0.65")),
                Arguments.of(CustomerOccupationGroup.ONLINE_SELLER, CollateralCategory.OTHER, new BigDecimal("0.60")),

                // 6. FREELANCER
                Arguments.of(CustomerOccupationGroup.FREELANCER, CollateralCategory.GOLD_JEWELRY, new BigDecimal("0.85")),
                Arguments.of(CustomerOccupationGroup.FREELANCER, CollateralCategory.MOTORBIKE, new BigDecimal("0.65")),
                Arguments.of(CustomerOccupationGroup.FREELANCER, CollateralCategory.CAR, new BigDecimal("0.65")),
                Arguments.of(CustomerOccupationGroup.FREELANCER, CollateralCategory.PHONE, new BigDecimal("0.60")),
                Arguments.of(CustomerOccupationGroup.FREELANCER, CollateralCategory.LAPTOP, new BigDecimal("0.60")),
                Arguments.of(CustomerOccupationGroup.FREELANCER, CollateralCategory.WATCH, new BigDecimal("0.55")),
                Arguments.of(CustomerOccupationGroup.FREELANCER, CollateralCategory.REAL_ESTATE, new BigDecimal("0.55")),
                Arguments.of(CustomerOccupationGroup.FREELANCER, CollateralCategory.OTHER, new BigDecimal("0.50"))
        );
    }

    @ParameterizedTest(name = "[{index}] {0} + {1} => LTV {2}")
    @MethodSource("provideAll48MatrixCombinations")
    @DisplayName("Verify Exact LTV Percent for all 48 (6 Occupations x 8 Categories) cells")
    void testAll48MatrixCombinationsAccuracy(CustomerOccupationGroup group, CollateralCategory category, BigDecimal expectedRatio) {
        BigDecimal actualLtv = policy.getMaxLtvPercent(group, category);
        assertNotNull(actualLtv, "LTV ratio must not be null for " + group + " and " + category);

        // Normalize if policy returns percentage (e.g. 90.00) vs decimal ratio (e.g. 0.90)
        BigDecimal normalizedActual = actualLtv.compareTo(BigDecimal.ONE) > 0
                ? actualLtv.divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP)
                : actualLtv;

        assertEquals(0, expectedRatio.compareTo(normalizedActual),
                String.format("Mismatch for %s x %s: expected %s but got %s", group, category, expectedRatio, normalizedActual));
    }

    // =========================================================================
    // 2. ABSOLUTE CEILING TEST: Loan > 100% of Appraised Value throws EXCEEDS_MAX_LTV
    // =========================================================================

    @Test
    @DisplayName("Absolute ceiling: loan > 100% of appraised value throws EXCEEDS_MAX_LTV")
    void testAbsoluteCeiling_Exceeds100Percent_ThrowsExceedsMaxLtv() {
        BigDecimal appraisedValue = new BigDecimal("10000000"); // 10 triệu
        BigDecimal loanAmountExceeding100 = new BigDecimal("10000001"); // 10 triệu + 1 đ

        List<String> validDocs = List.of("https://example.com/docs/payslip.png");
        VerificationDocumentType validDocType = VerificationDocumentType.PAYSLIP_BANK_STATEMENT;

        BusinessException ex = assertThrows(BusinessException.class, () ->
                policy.validateLoanRequest(
                        CustomerOccupationGroup.OFFICE_WORKER,
                        CollateralCategory.GOLD_JEWELRY, // Max is 92%, but testing > 100%
                        appraisedValue,
                        loanAmountExceeding100,
                        validDocs,
                        validDocType
                )
        );

        assertEquals(ErrorCode.EXCEEDS_MAX_LTV, ex.getErrorCode());
    }

    @Test
    @DisplayName("Absolute ceiling: loan 150% of appraised value throws EXCEEDS_MAX_LTV")
    void testAbsoluteCeiling_MajorExcess_ThrowsExceedsMaxLtv() {
        BigDecimal appraisedValue = new BigDecimal("50000000");
        BigDecimal loanAmount = new BigDecimal("75000000"); // 150%

        List<String> validDocs = List.of("https://example.com/docs/ct07.png");
        VerificationDocumentType validDocType = VerificationDocumentType.RESIDENCE_CONFIRMATION_CT07;

        BusinessException ex = assertThrows(BusinessException.class, () ->
                policy.validateLoanRequest(
                        CustomerOccupationGroup.FREELANCER,
                        CollateralCategory.MOTORBIKE,
                        appraisedValue,
                        loanAmount,
                        validDocs,
                        validDocType
                )
        );

        assertEquals(ErrorCode.EXCEEDS_MAX_LTV, ex.getErrorCode());
    }

    // =========================================================================
    // 3. CATEGORY-SPECIFIC BOUNDARY TESTS
    // =========================================================================

    @Test
    @DisplayName("Boundary: Office Worker + Motorbike (90% LTV) - exact boundary passes, +1 throws EXCEEDS_MAX_LTV")
    void testBoundary_OfficeWorker_Motorbike_90Percent() {
        BigDecimal appraised = new BigDecimal("10000000");
        BigDecimal exact90Percent = new BigDecimal("9000000");
        BigDecimal exceedBy1Vnd = new BigDecimal("9000001");

        List<String> docs = List.of("https://example.com/payslip.jpg");
        VerificationDocumentType docType = VerificationDocumentType.PAYSLIP_BANK_STATEMENT;

        // Exact 90% must PASS
        assertDoesNotThrow(() ->
                policy.validateLoanRequest(CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.MOTORBIKE, appraised, exact90Percent, docs, docType)
        );

        // 90% + 1 VND must THROW EXCEEDS_MAX_LTV
        BusinessException ex = assertThrows(BusinessException.class, () ->
                policy.validateLoanRequest(CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.MOTORBIKE, appraised, exceedBy1Vnd, docs, docType)
        );
        assertEquals(ErrorCode.EXCEEDS_MAX_LTV, ex.getErrorCode());
    }

    @Test
    @DisplayName("Boundary: Freelancer + Motorbike (65% LTV) - exact boundary passes, +1 throws EXCEEDS_MAX_LTV")
    void testBoundary_Freelancer_Motorbike_65Percent() {
        BigDecimal appraised = new BigDecimal("20000000");
        BigDecimal exact65Percent = new BigDecimal("13000000");
        BigDecimal exceedBy1Vnd = new BigDecimal("13000001");

        List<String> docs = List.of("https://example.com/ct07.jpg");
        VerificationDocumentType docType = VerificationDocumentType.RESIDENCE_CONFIRMATION_CT07;

        // Exact 65% must PASS
        assertDoesNotThrow(() ->
                policy.validateLoanRequest(CustomerOccupationGroup.FREELANCER, CollateralCategory.MOTORBIKE, appraised, exact65Percent, docs, docType)
        );

        // 65% + 1 VND must THROW
        BusinessException ex = assertThrows(BusinessException.class, () ->
                policy.validateLoanRequest(CustomerOccupationGroup.FREELANCER, CollateralCategory.MOTORBIKE, appraised, exceedBy1Vnd, docs, docType)
        );
        assertEquals(ErrorCode.EXCEEDS_MAX_LTV, ex.getErrorCode());
    }

    @Test
    @DisplayName("Boundary: Freelancer + Other (50% LTV) - exact boundary passes, +1 throws EXCEEDS_MAX_LTV")
    void testBoundary_Freelancer_Other_50Percent() {
        BigDecimal appraised = new BigDecimal("10000000");
        BigDecimal exact50Percent = new BigDecimal("5000000");
        BigDecimal exceedBy1Vnd = new BigDecimal("5000001");

        List<String> docs = List.of("https://example.com/rental.jpg");
        VerificationDocumentType docType = VerificationDocumentType.RENTAL_CONTRACT;

        assertDoesNotThrow(() ->
                policy.validateLoanRequest(CustomerOccupationGroup.FREELANCER, CollateralCategory.OTHER, appraised, exact50Percent, docs, docType)
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                policy.validateLoanRequest(CustomerOccupationGroup.FREELANCER, CollateralCategory.OTHER, appraised, exceedBy1Vnd, docs, docType)
        );
        assertEquals(ErrorCode.EXCEEDS_MAX_LTV, ex.getErrorCode());
    }

    // =========================================================================
    // 4. DOCUMENT TYPE MATCHING TESTS
    // =========================================================================

    @Test
    @DisplayName("Doc Type Matching: Valid doc types pass for all 6 groups")
    void testDocumentMatching_AllValidTypesPass() {
        BigDecimal appraised = new BigDecimal("10000000");
        BigDecimal loanAmount = new BigDecimal("3000000"); // Safe low amount (30%)
        List<String> docs = List.of("https://example.com/doc.png");

        // 1. OFFICE_WORKER
        for (VerificationDocumentType type : List.of(
                VerificationDocumentType.PAYSLIP_BANK_STATEMENT,
                VerificationDocumentType.LABOR_CONTRACT,
                VerificationDocumentType.SOCIAL_INSURANCE_VSSID)) {
            assertDoesNotThrow(() -> policy.validateLoanRequest(CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.OTHER, appraised, loanAmount, docs, type));
        }

        // 2. FACTORY_WORKER
        for (VerificationDocumentType type : List.of(
                VerificationDocumentType.PAYSLIP_BANK_STATEMENT,
                VerificationDocumentType.WORKER_ID_OR_CONTRACT,
                VerificationDocumentType.SOCIAL_INSURANCE_VSSID)) {
            assertDoesNotThrow(() -> policy.validateLoanRequest(CustomerOccupationGroup.FACTORY_WORKER, CollateralCategory.OTHER, appraised, loanAmount, docs, type));
        }

        // 3. TECH_DRIVER
        for (VerificationDocumentType type : List.of(
                VerificationDocumentType.DRIVER_APP_INCOME_SCREENSHOT,
                VerificationDocumentType.RESIDENCE_CONFIRMATION_CT07,
                VerificationDocumentType.RENTAL_CONTRACT)) {
            assertDoesNotThrow(() -> policy.validateLoanRequest(CustomerOccupationGroup.TECH_DRIVER, CollateralCategory.OTHER, appraised, loanAmount, docs, type));
        }

        // 4. SMALL_MERCHANT
        for (VerificationDocumentType type : List.of(
                VerificationDocumentType.BUSINESS_LICENSE_OR_STALL_LEASE,
                VerificationDocumentType.SHOP_PHOTO_AND_CASHFLOW)) {
            assertDoesNotThrow(() -> policy.validateLoanRequest(CustomerOccupationGroup.SMALL_MERCHANT, CollateralCategory.OTHER, appraised, loanAmount, docs, type));
        }

        // 5. ONLINE_SELLER
        for (VerificationDocumentType type : List.of(
                VerificationDocumentType.ECOMMERCE_REVENUE_SCREENSHOT,
                VerificationDocumentType.BANK_COD_STATEMENT)) {
            assertDoesNotThrow(() -> policy.validateLoanRequest(CustomerOccupationGroup.ONLINE_SELLER, CollateralCategory.OTHER, appraised, loanAmount, docs, type));
        }

        // 6. FREELANCER
        for (VerificationDocumentType type : List.of(
                VerificationDocumentType.RESIDENCE_CONFIRMATION_CT07,
                VerificationDocumentType.RENTAL_CONTRACT,
                VerificationDocumentType.UTILITY_BILL)) {
            assertDoesNotThrow(() -> policy.validateLoanRequest(CustomerOccupationGroup.FREELANCER, CollateralCategory.OTHER, appraised, loanAmount, docs, type));
        }
    }

    @Test
    @DisplayName("Doc Type Matching: Mismatched doc type throws INVALID_VERIFICATION_DOCUMENT")
    void testDocumentMatching_MismatchedDocType_ThrowsInvalidVerificationDocument() {
        BigDecimal appraised = new BigDecimal("10000000");
        BigDecimal loanAmount = new BigDecimal("5000000");
        List<String> docs = List.of("https://example.com/doc.png");

        // Office Worker with Driver App Screenshot
        BusinessException ex1 = assertThrows(BusinessException.class, () ->
                policy.validateLoanRequest(CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.PHONE, appraised, loanAmount, docs, VerificationDocumentType.DRIVER_APP_INCOME_SCREENSHOT)
        );
        assertEquals(ErrorCode.INVALID_VERIFICATION_DOCUMENT, ex1.getErrorCode());

        // Freelancer with Payslip
        BusinessException ex2 = assertThrows(BusinessException.class, () ->
                policy.validateLoanRequest(CustomerOccupationGroup.FREELANCER, CollateralCategory.PHONE, appraised, loanAmount, docs, VerificationDocumentType.PAYSLIP_BANK_STATEMENT)
        );
        assertEquals(ErrorCode.INVALID_VERIFICATION_DOCUMENT, ex2.getErrorCode());

        // Tech Driver with Business License
        BusinessException ex3 = assertThrows(BusinessException.class, () ->
                policy.validateLoanRequest(CustomerOccupationGroup.TECH_DRIVER, CollateralCategory.PHONE, appraised, loanAmount, docs, VerificationDocumentType.BUSINESS_LICENSE_OR_STALL_LEASE)
        );
        assertEquals(ErrorCode.INVALID_VERIFICATION_DOCUMENT, ex3.getErrorCode());

        // Small Merchant with Utility Bill
        BusinessException ex4 = assertThrows(BusinessException.class, () ->
                policy.validateLoanRequest(CustomerOccupationGroup.SMALL_MERCHANT, CollateralCategory.PHONE, appraised, loanAmount, docs, VerificationDocumentType.UTILITY_BILL)
        );
        assertEquals(ErrorCode.INVALID_VERIFICATION_DOCUMENT, ex4.getErrorCode());

        // Online Seller with Worker ID
        BusinessException ex5 = assertThrows(BusinessException.class, () ->
                policy.validateLoanRequest(CustomerOccupationGroup.ONLINE_SELLER, CollateralCategory.PHONE, appraised, loanAmount, docs, VerificationDocumentType.WORKER_ID_OR_CONTRACT)
        );
        assertEquals(ErrorCode.INVALID_VERIFICATION_DOCUMENT, ex5.getErrorCode());
    }

    @Test
    @DisplayName("Doc Type Matching: Empty or null doc URLs list throws MISSING_VERIFICATION_DOCUMENTS")
    void testDocumentMatching_EmptyOrNullDocUrls_ThrowsMissingVerificationDocuments() {
        BigDecimal appraised = new BigDecimal("10000000");
        BigDecimal loanAmount = new BigDecimal("5000000");

        // Null list
        BusinessException ex1 = assertThrows(BusinessException.class, () ->
                policy.validateLoanRequest(CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.MOTORBIKE, appraised, loanAmount, null, VerificationDocumentType.PAYSLIP_BANK_STATEMENT)
        );
        assertEquals(ErrorCode.MISSING_VERIFICATION_DOCUMENTS, ex1.getErrorCode());

        // Empty list
        BusinessException ex2 = assertThrows(BusinessException.class, () ->
                policy.validateLoanRequest(CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.MOTORBIKE, appraised, loanAmount, Collections.emptyList(), VerificationDocumentType.PAYSLIP_BANK_STATEMENT)
        );
        assertEquals(ErrorCode.MISSING_VERIFICATION_DOCUMENTS, ex2.getErrorCode());

        // Null doc type
        BusinessException ex3 = assertThrows(BusinessException.class, () ->
                policy.validateLoanRequest(CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.MOTORBIKE, appraised, loanAmount, List.of("https://example.com/doc.png"), null)
        );
        assertEquals(ErrorCode.MISSING_VERIFICATION_DOCUMENTS, ex3.getErrorCode());
    }

    // =========================================================================
    // 5. MAX LOAN AMOUNT CALCULATION HELPER
    // =========================================================================

    @Test
    @DisplayName("Calculate Max Loan Amount: 80,000,000 SH 150i with Office Worker (90%) => 72,000,000")
    void testCalculateMaxLoanAmount_OfficeWorker_Motorbike() {
        BigDecimal appraised = new BigDecimal("80000000");
        BigDecimal maxLoan = policy.calculateMaxLoanAmount(CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.MOTORBIKE, appraised);

        assertEquals(0, new BigDecimal("72000000.00").compareTo(maxLoan));
    }

    @Test
    @DisplayName("Calculate Max Loan Amount: 80,000,000 SH 150i with Freelancer (65%) => 52,000,000")
    void testCalculateMaxLoanAmount_Freelancer_Motorbike() {
        BigDecimal appraised = new BigDecimal("80000000");
        BigDecimal maxLoan = policy.calculateMaxLoanAmount(CustomerOccupationGroup.FREELANCER, CollateralCategory.MOTORBIKE, appraised);

        assertEquals(0, new BigDecimal("52000000.00").compareTo(maxLoan));
    }
}

package com.pawnmaster.api.modules.collateral;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.branch.BranchRepository;
import com.pawnmaster.api.modules.collateral.dto.CollateralCreateRequest;
import com.pawnmaster.api.modules.collateral.dto.CollateralResponse;
import com.pawnmaster.api.modules.collateral.dto.LtvEvaluationRequest;
import com.pawnmaster.api.modules.collateral.dto.LtvEvaluationResponse;
import com.pawnmaster.api.modules.customer.CustomerEntity;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.CustomerRepository;
import com.pawnmaster.api.modules.customer.CustomerServiceImpl;
import com.pawnmaster.api.modules.customer.VerificationDocumentType;
import com.pawnmaster.api.modules.customer.dto.CustomerCreateRequest;
import com.pawnmaster.api.modules.customer.dto.CustomerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Milestone 2 Boundary & Adversarial Challenge Empirical Test Suite.
 *
 * Verifies:
 * 1. Complete 48-cell 2D LTV matrix integrity (6 Occupation Groups x 8
 * Collateral Categories).
 * 2. 100% hard ceiling rule (loan = appraisal, loan = appraisal + 1 VND, loan
 * <= 0).
 * 3. Matrix LTV ceiling rule boundary checks (loan <= maxAllowedLoan passes,
 * loan = maxAllowedLoan + 1 VND fails).
 * 4. Document matching verification matrix (valid docs, invalid doc types,
 * empty/whitespace URLs).
 * 5. Service-layer boundary stress tests (CustomerService and
 * CollateralService).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Milestone 2 Boundary & Adversarial Empirical Challenge Suite")
public class Milestone2BoundaryEmpiricalTest {

        private LtvPolicyService policy;

        @Mock
        private CollateralRepository collateralRepository;

        @Mock
        private CustomerRepository customerRepository;

        @Mock
        private BranchRepository branchRepository;

        @Spy
        private LtvPolicyService ltvPolicyService = LtvPolicyTestHelper.createDefaultPolicyService();

        @Spy
        private com.pawnmaster.api.modules.customer.VerificationDocumentPolicy verificationDocumentPolicy = new com.pawnmaster.api.modules.customer.VerificationDocumentPolicy();

        @InjectMocks
        private CollateralServiceImpl collateralService;

        @InjectMocks
        private CustomerServiceImpl customerService;

        @BeforeEach
        void setUp() {
                policy = LtvPolicyTestHelper.createDefaultPolicyService();
        }

        // =========================================================================
        // SECTION 1: ALL 48 CELLS MATRIX VERIFICATION (6 Groups x 8 Categories)
        // =========================================================================

        @Nested
        @DisplayName("1. Exhaustive 48-Cell Matrix Completeness & Boundary Values")
        class MatrixAll48CellsEmpiricalTests {

                @Test
                @DisplayName("All 48 cells return non-null BigDecimal between 0.00 and 1.00")
                void testAll48CellsNonNullAndValidRange() {
                        int cellCount = 0;
                        for (CustomerOccupationGroup group : CustomerOccupationGroup.values()) {
                                for (CollateralCategory category : CollateralCategory.values()) {
                                        cellCount++;
                                        BigDecimal ltv = policy.getMaxLtvPercent(group, category);

                                        assertNotNull(ltv, String.format("Cell [%s x %s] must not be null", group,
                                                        category));
                                        assertTrue(ltv.compareTo(BigDecimal.ZERO) > 0,
                                                        String.format("Cell [%s x %s] LTV must be > 0.00, got: %s",
                                                                        group, category, ltv));
                                        assertTrue(ltv.compareTo(BigDecimal.ONE) <= 0,
                                                        String.format("Cell [%s x %s] LTV must be <= 1.00 (100%% hard cap), got: %s",
                                                                        group, category, ltv));
                                        assertEquals(ltv, policy.getMaxLtvRatio(group, category),
                                                        "getMaxLtvRatio must match getMaxLtvPercent exactly");
                                }
                        }
                        assertEquals(48, cellCount, "Total evaluated matrix cells must be exactly 48 (6 x 8)");
                }

                static Stream<Arguments> provideExpected48MatrixCells() {
                        return Stream.of(
                                        // 1. OFFICE_WORKER
                                        Arguments.of(CustomerOccupationGroup.OFFICE_WORKER,
                                                        CollateralCategory.GOLD_JEWELRY, new BigDecimal("0.92")),
                                        Arguments.of(CustomerOccupationGroup.OFFICE_WORKER,
                                                        CollateralCategory.MOTORBIKE, new BigDecimal("0.90")),
                                        Arguments.of(CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.CAR,
                                                        new BigDecimal("0.85")),
                                        Arguments.of(CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.PHONE,
                                                        new BigDecimal("0.80")),
                                        Arguments.of(CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.LAPTOP,
                                                        new BigDecimal("0.80")),
                                        Arguments.of(CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.WATCH,
                                                        new BigDecimal("0.75")),
                                        Arguments.of(CustomerOccupationGroup.OFFICE_WORKER,
                                                        CollateralCategory.REAL_ESTATE, new BigDecimal("0.75")),
                                        Arguments.of(CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.OTHER,
                                                        new BigDecimal("0.70")),

                                        // 2. FACTORY_WORKER
                                        Arguments.of(CustomerOccupationGroup.FACTORY_WORKER,
                                                        CollateralCategory.GOLD_JEWELRY, new BigDecimal("0.90")),
                                        Arguments.of(CustomerOccupationGroup.FACTORY_WORKER,
                                                        CollateralCategory.MOTORBIKE, new BigDecimal("0.80")),
                                        Arguments.of(CustomerOccupationGroup.FACTORY_WORKER, CollateralCategory.CAR,
                                                        new BigDecimal("0.75")),
                                        Arguments.of(CustomerOccupationGroup.FACTORY_WORKER, CollateralCategory.PHONE,
                                                        new BigDecimal("0.75")),
                                        Arguments.of(CustomerOccupationGroup.FACTORY_WORKER, CollateralCategory.LAPTOP,
                                                        new BigDecimal("0.75")),
                                        Arguments.of(CustomerOccupationGroup.FACTORY_WORKER, CollateralCategory.WATCH,
                                                        new BigDecimal("0.70")),
                                        Arguments.of(CustomerOccupationGroup.FACTORY_WORKER,
                                                        CollateralCategory.REAL_ESTATE, new BigDecimal("0.70")),
                                        Arguments.of(CustomerOccupationGroup.FACTORY_WORKER, CollateralCategory.OTHER,
                                                        new BigDecimal("0.65")),

                                        // 3. TECH_DRIVER
                                        Arguments.of(CustomerOccupationGroup.TECH_DRIVER,
                                                        CollateralCategory.GOLD_JEWELRY, new BigDecimal("0.88")),
                                        Arguments.of(CustomerOccupationGroup.TECH_DRIVER, CollateralCategory.MOTORBIKE,
                                                        new BigDecimal("0.70")),
                                        Arguments.of(CustomerOccupationGroup.TECH_DRIVER, CollateralCategory.CAR,
                                                        new BigDecimal("0.70")),
                                        Arguments.of(CustomerOccupationGroup.TECH_DRIVER, CollateralCategory.PHONE,
                                                        new BigDecimal("0.70")),
                                        Arguments.of(CustomerOccupationGroup.TECH_DRIVER, CollateralCategory.LAPTOP,
                                                        new BigDecimal("0.70")),
                                        Arguments.of(CustomerOccupationGroup.TECH_DRIVER, CollateralCategory.WATCH,
                                                        new BigDecimal("0.65")),
                                        Arguments.of(CustomerOccupationGroup.TECH_DRIVER,
                                                        CollateralCategory.REAL_ESTATE, new BigDecimal("0.65")),
                                        Arguments.of(CustomerOccupationGroup.TECH_DRIVER, CollateralCategory.OTHER,
                                                        new BigDecimal("0.60")),

                                        // 4. SMALL_MERCHANT
                                        Arguments.of(CustomerOccupationGroup.SMALL_MERCHANT,
                                                        CollateralCategory.GOLD_JEWELRY, new BigDecimal("0.90")),
                                        Arguments.of(CustomerOccupationGroup.SMALL_MERCHANT,
                                                        CollateralCategory.MOTORBIKE, new BigDecimal("0.80")),
                                        Arguments.of(CustomerOccupationGroup.SMALL_MERCHANT, CollateralCategory.CAR,
                                                        new BigDecimal("0.80")),
                                        Arguments.of(CustomerOccupationGroup.SMALL_MERCHANT, CollateralCategory.PHONE,
                                                        new BigDecimal("0.75")),
                                        Arguments.of(CustomerOccupationGroup.SMALL_MERCHANT, CollateralCategory.LAPTOP,
                                                        new BigDecimal("0.75")),
                                        Arguments.of(CustomerOccupationGroup.SMALL_MERCHANT, CollateralCategory.WATCH,
                                                        new BigDecimal("0.75")),
                                        Arguments.of(CustomerOccupationGroup.SMALL_MERCHANT,
                                                        CollateralCategory.REAL_ESTATE, new BigDecimal("0.70")),
                                        Arguments.of(CustomerOccupationGroup.SMALL_MERCHANT, CollateralCategory.OTHER,
                                                        new BigDecimal("0.65")),

                                        // 5. ONLINE_SELLER
                                        Arguments.of(CustomerOccupationGroup.ONLINE_SELLER,
                                                        CollateralCategory.GOLD_JEWELRY, new BigDecimal("0.88")),
                                        Arguments.of(CustomerOccupationGroup.ONLINE_SELLER,
                                                        CollateralCategory.MOTORBIKE, new BigDecimal("0.75")),
                                        Arguments.of(CustomerOccupationGroup.ONLINE_SELLER, CollateralCategory.CAR,
                                                        new BigDecimal("0.75")),
                                        Arguments.of(CustomerOccupationGroup.ONLINE_SELLER, CollateralCategory.PHONE,
                                                        new BigDecimal("0.70")),
                                        Arguments.of(CustomerOccupationGroup.ONLINE_SELLER, CollateralCategory.LAPTOP,
                                                        new BigDecimal("0.70")),
                                        Arguments.of(CustomerOccupationGroup.ONLINE_SELLER, CollateralCategory.WATCH,
                                                        new BigDecimal("0.68")),
                                        Arguments.of(CustomerOccupationGroup.ONLINE_SELLER,
                                                        CollateralCategory.REAL_ESTATE, new BigDecimal("0.65")),
                                        Arguments.of(CustomerOccupationGroup.ONLINE_SELLER, CollateralCategory.OTHER,
                                                        new BigDecimal("0.60")),

                                        // 6. FREELANCER
                                        Arguments.of(CustomerOccupationGroup.FREELANCER,
                                                        CollateralCategory.GOLD_JEWELRY, new BigDecimal("0.85")),
                                        Arguments.of(CustomerOccupationGroup.FREELANCER, CollateralCategory.MOTORBIKE,
                                                        new BigDecimal("0.65")),
                                        Arguments.of(CustomerOccupationGroup.FREELANCER, CollateralCategory.CAR,
                                                        new BigDecimal("0.65")),
                                        Arguments.of(CustomerOccupationGroup.FREELANCER, CollateralCategory.PHONE,
                                                        new BigDecimal("0.60")),
                                        Arguments.of(CustomerOccupationGroup.FREELANCER, CollateralCategory.LAPTOP,
                                                        new BigDecimal("0.60")),
                                        Arguments.of(CustomerOccupationGroup.FREELANCER, CollateralCategory.WATCH,
                                                        new BigDecimal("0.55")),
                                        Arguments.of(CustomerOccupationGroup.FREELANCER, CollateralCategory.REAL_ESTATE,
                                                        new BigDecimal("0.55")),
                                        Arguments.of(CustomerOccupationGroup.FREELANCER, CollateralCategory.OTHER,
                                                        new BigDecimal("0.50")));
                }

                @ParameterizedTest(name = "[{index}] {0} x {1} => LTV {2}")
                @MethodSource("provideExpected48MatrixCells")
                @DisplayName("Verify precise decimal ratio for each of 48 individual cells")
                void testExactMatrixCellRatio(CustomerOccupationGroup group, CollateralCategory category,
                                BigDecimal expectedRatio) {
                        BigDecimal actualLtv = policy.getMaxLtvPercent(group, category);
                        assertEquals(0, expectedRatio.compareTo(actualLtv),
                                        String.format("LTV value mismatch for [%s x %s]. Expected: %s, got: %s",
                                                        group, category, expectedRatio, actualLtv));
                }

                @Test
                @DisplayName("Null group or null category throws INVALID_REQUEST")
                void testNullGroupOrCategoryThrowsInvalidRequest() {
                        BusinessException ex1 = assertThrows(BusinessException.class,
                                        () -> policy.getMaxLtvPercent(null, CollateralCategory.MOTORBIKE));
                        assertEquals(ErrorCode.INVALID_REQUEST, ex1.getErrorCode());

                        BusinessException ex2 = assertThrows(BusinessException.class,
                                        () -> policy.getMaxLtvPercent(CustomerOccupationGroup.OFFICE_WORKER, null));
                        assertEquals(ErrorCode.INVALID_REQUEST, ex2.getErrorCode());
                }
        }

        // =========================================================================
        // SECTION 2: 100% HARD CEILING RULE STRESS TESTS
        // =========================================================================

        @Nested
        @DisplayName("2. 100% Hard Ceiling Rule Stress Tests")
        class HardCeiling100PercentEmpiricalTests {

                private final List<String> validDocs = List.of("https://cdn.pawnmaster.vn/docs/payslip.png");
                private final VerificationDocumentType validDoc = VerificationDocumentType.PAYSLIP_BANK_STATEMENT;

                @Test
                @DisplayName("Exceeds 100% appraised value by exactly 1 VND throws EXCEEDS_MAX_LTV")
                void testExceedsAppraisedValueBy1Vnd_ThrowsExceedsMaxLtv() {
                        BigDecimal appraisedValue = new BigDecimal("50000000"); // 50 triệu
                        BigDecimal loanAmountExceedingBy1 = new BigDecimal("50000001"); // 50 triệu + 1 đ

                        BusinessException ex = assertThrows(BusinessException.class, () -> policy.validateLoanRequest(
                                        CustomerOccupationGroup.OFFICE_WORKER,
                                        CollateralCategory.GOLD_JEWELRY,
                                        appraisedValue,
                                        loanAmountExceedingBy1,
                                        validDocs,
                                        validDoc));
                        assertEquals(ErrorCode.EXCEEDS_MAX_LTV, ex.getErrorCode());
                        assertTrue(ex.getMessage().contains("vượt quá trần 100% giá trị định giá tài sản"));
                }

                @Test
                @DisplayName("Grossly exceeding 100% appraised value (150%, 200%, 1000%) throws EXCEEDS_MAX_LTV")
                void testExtremeLoanAmountsExceeding100Percent_ThrowsExceedsMaxLtv() {
                        BigDecimal appraisedValue = new BigDecimal("10000000"); // 10 triệu

                        BigDecimal[] extremeAmounts = new BigDecimal[] {
                                        new BigDecimal("15000000"), // 150%
                                        new BigDecimal("20000000"), // 200%
                                        new BigDecimal("100000000") // 1000%
                        };

                        for (BigDecimal extremeLoan : extremeAmounts) {
                                BusinessException ex = assertThrows(BusinessException.class,
                                                () -> policy.validateLoanRequest(
                                                                CustomerOccupationGroup.OFFICE_WORKER,
                                                                CollateralCategory.GOLD_JEWELRY,
                                                                appraisedValue,
                                                                extremeLoan,
                                                                validDocs,
                                                                validDoc));
                                assertEquals(ErrorCode.EXCEEDS_MAX_LTV, ex.getErrorCode());
                        }
                }

                @Test
                @DisplayName("Loan amount exactly 100% of appraised value throws EXCEEDS_MAX_LTV because category LTV < 100%")
                void testLoanAmountEqualsAppraisedValue_ThrowsExceedsMaxLtv() {
                        BigDecimal appraisedValue = new BigDecimal("10000000");
                        BigDecimal loanAmount100Percent = new BigDecimal("10000000");

                        // Even for highest LTV (Office Worker + Gold = 92%), 100% must be rejected
                        BusinessException ex = assertThrows(BusinessException.class, () -> policy.validateLoanRequest(
                                        CustomerOccupationGroup.OFFICE_WORKER,
                                        CollateralCategory.GOLD_JEWELRY,
                                        appraisedValue,
                                        loanAmount100Percent,
                                        validDocs,
                                        validDoc));
                        assertEquals(ErrorCode.EXCEEDS_MAX_LTV, ex.getErrorCode());
                }

                @Test
                @DisplayName("Zero or negative loan amount throws INVALID_REQUEST")
                void testZeroOrNegativeLoanAmount_ThrowsInvalidRequest() {
                        BigDecimal appraisedValue = new BigDecimal("10000000");

                        // Loan amount = 0
                        BusinessException ex1 = assertThrows(BusinessException.class, () -> policy.validateLoanRequest(
                                        CustomerOccupationGroup.OFFICE_WORKER,
                                        CollateralCategory.GOLD_JEWELRY,
                                        appraisedValue,
                                        BigDecimal.ZERO,
                                        validDocs,
                                        validDoc));
                        assertEquals(ErrorCode.INVALID_REQUEST, ex1.getErrorCode());

                        // Loan amount = -500,000 VND
                        BusinessException ex2 = assertThrows(BusinessException.class, () -> policy.validateLoanRequest(
                                        CustomerOccupationGroup.OFFICE_WORKER,
                                        CollateralCategory.GOLD_JEWELRY,
                                        appraisedValue,
                                        new BigDecimal("-500000"),
                                        validDocs,
                                        validDoc));
                        assertEquals(ErrorCode.INVALID_REQUEST, ex2.getErrorCode());
                }

                @Test
                @DisplayName("Zero or negative appraised value throws INVALID_REQUEST")
                void testZeroOrNegativeAppraisedValue_ThrowsInvalidRequest() {
                        BigDecimal loanAmount = new BigDecimal("5000000");

                        // Appraised value = 0
                        BusinessException ex1 = assertThrows(BusinessException.class, () -> policy.validateLoanRequest(
                                        CustomerOccupationGroup.OFFICE_WORKER,
                                        CollateralCategory.GOLD_JEWELRY,
                                        BigDecimal.ZERO,
                                        loanAmount,
                                        validDocs,
                                        validDoc));
                        assertEquals(ErrorCode.INVALID_REQUEST, ex1.getErrorCode());

                        // Appraised value = -10,000,000
                        BusinessException ex2 = assertThrows(BusinessException.class, () -> policy.validateLoanRequest(
                                        CustomerOccupationGroup.OFFICE_WORKER,
                                        CollateralCategory.GOLD_JEWELRY,
                                        new BigDecimal("-10000000"),
                                        loanAmount,
                                        validDocs,
                                        validDoc));
                        assertEquals(ErrorCode.INVALID_REQUEST, ex2.getErrorCode());
                }

                @Test
                @DisplayName("Null appraised value or null loan amount throws INVALID_REQUEST")
                void testNullValues_ThrowsInvalidRequest() {
                        BusinessException ex1 = assertThrows(BusinessException.class, () -> policy.validateLoanRequest(
                                        CustomerOccupationGroup.OFFICE_WORKER,
                                        CollateralCategory.GOLD_JEWELRY,
                                        null,
                                        new BigDecimal("5000000"),
                                        validDocs,
                                        validDoc));
                        assertEquals(ErrorCode.INVALID_REQUEST, ex1.getErrorCode());

                        BusinessException ex2 = assertThrows(BusinessException.class, () -> policy.validateLoanRequest(
                                        CustomerOccupationGroup.OFFICE_WORKER,
                                        CollateralCategory.GOLD_JEWELRY,
                                        new BigDecimal("10000000"),
                                        null,
                                        validDocs,
                                        validDoc));
                        assertEquals(ErrorCode.INVALID_REQUEST, ex2.getErrorCode());
                }
        }

        // =========================================================================
        // SECTION 3: MATRIX LTV CEILING BOUNDARY EMPIRICAL TESTS (ALL 48 CELLS)
        // =========================================================================

        @Nested
        @DisplayName("3. Matrix LTV Ceiling Boundary Empirical Tests")
        class MatrixLtvCeilingBoundaryEmpiricalTests {

                private VerificationDocumentType getValidDocForGroup(CustomerOccupationGroup group) {
                        return policy.getAllowedDocuments(group).iterator().next();
                }

                @ParameterizedTest(name = "[{index}] Boundary stress for {0} x {1}")
                @MethodSource("com.pawnmaster.api.modules.collateral.Milestone2BoundaryEmpiricalTest$MatrixAll48CellsEmpiricalTests#provideExpected48MatrixCells")
                @DisplayName("Exhaustive boundary check on all 48 cells: exact passes, +1 VND fails, -1 VND passes")
                void testBoundaryOnAll48Cells(CustomerOccupationGroup group, CollateralCategory category,
                                BigDecimal ltvRatio) {
                        BigDecimal appraisedValue = new BigDecimal("100000000"); // 100 triệu VND
                        BigDecimal maxAllowedLoan = policy.calculateMaxLoanAmount(group, category, appraisedValue);

                        // Sanity check maxAllowedLoan = 100,000,000 * ltvRatio
                        BigDecimal expectedLoan = appraisedValue.multiply(ltvRatio).setScale(2, RoundingMode.HALF_UP);
                        assertEquals(0, expectedLoan.compareTo(maxAllowedLoan));

                        List<String> docs = List.of("https://cdn.pawnmaster.vn/docs/test.jpg");
                        VerificationDocumentType docType = getValidDocForGroup(group);

                        // 1. Exact Boundary: loanAmount = maxAllowedLoan MUST PASS
                        assertDoesNotThrow(() -> policy.validateLoanRequest(group, category, appraisedValue,
                                        maxAllowedLoan, docs, docType));

                        // 2. Over Boundary by 1 VND: loanAmount = maxAllowedLoan + 1 VND MUST THROW
                        // EXCEEDS_MAX_LTV
                        BigDecimal overBy1 = maxAllowedLoan.add(BigDecimal.ONE);
                        BusinessException exOver = assertThrows(BusinessException.class, () -> policy
                                        .validateLoanRequest(group, category, appraisedValue, overBy1, docs, docType));
                        assertEquals(ErrorCode.EXCEEDS_MAX_LTV, exOver.getErrorCode());

                        // 3. Under Boundary by 1 VND: loanAmount = maxAllowedLoan - 1 VND MUST PASS
                        BigDecimal underBy1 = maxAllowedLoan.subtract(BigDecimal.ONE);
                        assertDoesNotThrow(() -> policy.validateLoanRequest(group, category, appraisedValue, underBy1,
                                        docs, docType));
                }

                @Test
                @DisplayName("Odd/Indivisible amount rounding boundary: 15,357,913 VND appraisal")
                void testOddAmountPrecisionBoundary() {
                        BigDecimal appraisedValue = new BigDecimal("15357913.00");
                        CustomerOccupationGroup group = CustomerOccupationGroup.ONLINE_SELLER;
                        CollateralCategory category = CollateralCategory.WATCH; // LTV = 0.68

                        BigDecimal maxLoan = policy.calculateMaxLoanAmount(group, category, appraisedValue);
                        // 15357913 * 0.68 = 10443380.84
                        assertEquals(new BigDecimal("10443380.84"), maxLoan);

                        List<String> docs = List.of("https://cdn.pawnmaster.vn/docs/ecommerce.png");
                        VerificationDocumentType docType = VerificationDocumentType.ECOMMERCE_REVENUE_SCREENSHOT;

                        // Exact boundary passes
                        assertDoesNotThrow(() -> policy.validateLoanRequest(group, category, appraisedValue, maxLoan,
                                        docs, docType));

                        // Over by 0.01 VND fails
                        BigDecimal overByPenny = maxLoan.add(new BigDecimal("0.01"));
                        BusinessException ex = assertThrows(BusinessException.class,
                                        () -> policy.validateLoanRequest(group, category, appraisedValue, overByPenny,
                                                        docs, docType));
                        assertEquals(ErrorCode.EXCEEDS_MAX_LTV, ex.getErrorCode());
                }

                @Test
                @DisplayName("Large institutional appraisal boundary: 5,000,000,000 VND Real Estate")
                void testLargeAppraisalBoundary() {
                        BigDecimal appraisedValue = new BigDecimal("5000000000.00"); // 5 tỷ
                        CustomerOccupationGroup group = CustomerOccupationGroup.OFFICE_WORKER;
                        CollateralCategory category = CollateralCategory.REAL_ESTATE; // 75% LTV -> 3.75 tỷ

                        BigDecimal maxLoan = policy.calculateMaxLoanAmount(group, category, appraisedValue);
                        assertEquals(new BigDecimal("3750000000.00"), maxLoan);

                        List<String> docs = List.of("https://cdn.pawnmaster.vn/docs/contract.pdf");
                        VerificationDocumentType docType = VerificationDocumentType.LABOR_CONTRACT;

                        assertDoesNotThrow(() -> policy.validateLoanRequest(group, category, appraisedValue, maxLoan,
                                        docs, docType));

                        BigDecimal overBy1Vnd = maxLoan.add(BigDecimal.ONE);
                        BusinessException ex = assertThrows(BusinessException.class,
                                        () -> policy.validateLoanRequest(group, category, appraisedValue, overBy1Vnd,
                                                        docs, docType));
                        assertEquals(ErrorCode.EXCEEDS_MAX_LTV, ex.getErrorCode());
                }
        }

        // =========================================================================
        // SECTION 4: DOCUMENT MATCHING VERIFICATION (ALL GROUPS x ALL DOC TYPES)
        // =========================================================================

        @Nested
        @DisplayName("4. Document Matching Verification & Exact ErrorCodes")
        class DocumentMatchingVerificationEmpiricalTests {

                private final List<String> standardDocUrls = List.of("https://cdn.pawnmaster.vn/uploads/doc1.jpg");

                @Test
                @DisplayName("Exhaustive verification: All 6 groups x all 12 document types check exact ErrorCode")
                void testExhaustiveDocumentMatchingAcrossAllGroupsAndDocTypes() {
                        int validCombinations = 0;
                        int invalidCombinations = 0;

                        for (CustomerOccupationGroup group : CustomerOccupationGroup.values()) {
                                Set<VerificationDocumentType> allowedDocs = policy.getAllowedDocuments(group);
                                assertFalse(allowedDocs.isEmpty(),
                                                "Allowed documents must not be empty for group: " + group);

                                for (VerificationDocumentType docType : VerificationDocumentType.values()) {
                                        if (allowedDocs.contains(docType)) {
                                                // VALID: must return true and must not throw
                                                assertTrue(policy.isDocumentValidForGroup(group, docType),
                                                                String.format("Expected %s to be valid for %s", docType,
                                                                                group));
                                                assertDoesNotThrow(() -> policy.validateVerificationDocuments(group,
                                                                docType, standardDocUrls));
                                                validCombinations++;
                                        } else {
                                                // INVALID: must return false and must throw
                                                // INVALID_VERIFICATION_DOCUMENT
                                                assertFalse(policy.isDocumentValidForGroup(group, docType),
                                                                String.format("Expected %s to be INVALID for %s",
                                                                                docType, group));
                                                BusinessException ex = assertThrows(BusinessException.class,
                                                                () -> policy.validateVerificationDocuments(group,
                                                                                docType, standardDocUrls));
                                                assertEquals(ErrorCode.INVALID_VERIFICATION_DOCUMENT, ex.getErrorCode(),
                                                                String.format("Expected INVALID_VERIFICATION_DOCUMENT for %s with %s",
                                                                                group, docType));
                                                invalidCombinations++;
                                        }
                                }
                        }

                        assertEquals(6 * 12, validCombinations + invalidCombinations,
                                        "Total document matching evaluations must equal 72 (6 groups x 12 doc types)");
                }

                @Test
                @DisplayName("Missing documents: Null docUrls throws MISSING_VERIFICATION_DOCUMENTS")
                void testMissingDocumentsNullUrls_ThrowsMissingVerificationDocuments() {
                        BusinessException ex = assertThrows(BusinessException.class,
                                        () -> policy.validateVerificationDocuments(
                                                        CustomerOccupationGroup.OFFICE_WORKER,
                                                        VerificationDocumentType.PAYSLIP_BANK_STATEMENT,
                                                        null));
                        assertEquals(ErrorCode.MISSING_VERIFICATION_DOCUMENTS, ex.getErrorCode());
                }

                @Test
                @DisplayName("Missing documents: Empty docUrls list throws MISSING_VERIFICATION_DOCUMENTS")
                void testMissingDocumentsEmptyUrls_ThrowsMissingVerificationDocuments() {
                        BusinessException ex = assertThrows(BusinessException.class,
                                        () -> policy.validateVerificationDocuments(
                                                        CustomerOccupationGroup.OFFICE_WORKER,
                                                        VerificationDocumentType.PAYSLIP_BANK_STATEMENT,
                                                        Collections.emptyList()));
                        assertEquals(ErrorCode.MISSING_VERIFICATION_DOCUMENTS, ex.getErrorCode());
                }

                @Test
                @DisplayName("Missing documents: Blank or whitespace-only URLs throw MISSING_VERIFICATION_DOCUMENTS")
                void testMissingDocumentsBlankUrls_ThrowsMissingVerificationDocuments() {
                        List<String> blankUrls = List.of("", "   ", "\t\n  ");
                        BusinessException ex = assertThrows(BusinessException.class,
                                        () -> policy.validateVerificationDocuments(
                                                        CustomerOccupationGroup.OFFICE_WORKER,
                                                        VerificationDocumentType.PAYSLIP_BANK_STATEMENT,
                                                        blankUrls));
                        assertEquals(ErrorCode.MISSING_VERIFICATION_DOCUMENTS, ex.getErrorCode());
                }

                @Test
                @DisplayName("Missing documents: Null docType throws MISSING_VERIFICATION_DOCUMENTS")
                void testMissingDocumentsNullDocType_ThrowsMissingVerificationDocuments() {
                        BusinessException ex = assertThrows(BusinessException.class,
                                        () -> policy.validateVerificationDocuments(
                                                        CustomerOccupationGroup.OFFICE_WORKER,
                                                        null,
                                                        standardDocUrls));
                        assertEquals(ErrorCode.MISSING_VERIFICATION_DOCUMENTS, ex.getErrorCode());
                }

                @Test
                @DisplayName("Partially blank URLs list with at least one valid URL passes")
                void testPartiallyBlankUrlsWithOneValidUrlPasses() {
                        List<String> mixedUrls = List.of("", "   ", "https://cdn.pawnmaster.vn/uploads/valid.png");
                        assertDoesNotThrow(() -> policy.validateVerificationDocuments(
                                        CustomerOccupationGroup.OFFICE_WORKER,
                                        VerificationDocumentType.PAYSLIP_BANK_STATEMENT,
                                        mixedUrls));
                }
        }

        // =========================================================================
        // SECTION 5: SERVICE-LAYER EMPIRICAL INTEGRATION TESTS
        // =========================================================================

        @Nested
        @DisplayName("5. Service-Layer Boundary Stress Tests (CollateralService & CustomerService)")
        class ServiceLayerBoundaryEmpiricalTests {

                private BranchEntity mockBranch;
                private CustomerEntity mockCustomer;

                @BeforeEach
                void initMocks() {
                        mockBranch = BranchEntity.builder()
                                        .branchCode("CN-TEST-01")
                                        .branchName("Chi nhánh Test")
                                        .active(true)
                                        .build();
                        mockBranch.setId(100L);

                        mockCustomer = CustomerEntity.builder()
                                        .customerCode("KH-TEST-001")
                                        .fullName("Nguyễn Văn Thử Nghiệm")
                                        .identityNumber("079090001234")
                                        .occupationGroup(CustomerOccupationGroup.TECH_DRIVER)
                                        .verificationDocType(VerificationDocumentType.DRIVER_APP_INCOME_SCREENSHOT)
                                        .verificationDocUrls("https://cdn.pawnmaster.vn/driver.png")
                                        .build();
                        mockCustomer.setId(200L);
                }

                @Test
                @DisplayName("CollateralService.evaluateLtv: Loan within matrix limit returns eligible = true")
                void testEvaluateLtv_WithinMatrixLimit_ReturnsEligibleTrue() {
                        LtvEvaluationRequest req = LtvEvaluationRequest.builder()
                                        .occupationGroup(CustomerOccupationGroup.TECH_DRIVER)
                                        .category(CollateralCategory.MOTORBIKE) // 70% LTV
                                        .appraisedValue(new BigDecimal("50000000"))
                                        .requestedLoanAmount(new BigDecimal("35000000")) // Exactly 70%
                                        .build();

                        LtvEvaluationResponse resp = collateralService.evaluateLtv(req);

                        assertNotNull(resp);
                        assertTrue(resp.isEligible());
                        assertEquals(new BigDecimal("0.70"), resp.getMaxLtvPercent());
                        assertEquals(0, new BigDecimal("35000000.00").compareTo(resp.getMaxAllowedLoanAmount()));
                }

                @Test
                @DisplayName("CollateralService.evaluateLtv: Loan over matrix limit but <= 100% returns eligible = false without throwing")
                void testEvaluateLtv_OverMatrixLimit_ReturnsEligibleFalse() {
                        LtvEvaluationRequest req = LtvEvaluationRequest.builder()
                                        .occupationGroup(CustomerOccupationGroup.TECH_DRIVER)
                                        .category(CollateralCategory.MOTORBIKE) // 70% LTV -> max 35M
                                        .appraisedValue(new BigDecimal("50000000"))
                                        .requestedLoanAmount(new BigDecimal("40000000")) // 40M > 35M, but <= 50M
                                        .build();

                        LtvEvaluationResponse resp = collateralService.evaluateLtv(req);

                        assertNotNull(resp);
                        assertFalse(resp.isEligible());
                        assertTrue(resp.getEligibleMessage().contains("vượt quá hạn mức tối đa cho phép"));
                }

                @Test
                @DisplayName("CollateralService.evaluateLtv: Loan over 100% hard ceiling throws EXCEEDS_MAX_LTV")
                void testEvaluateLtv_Exceeds100PercentHardCeiling_ThrowsExceedsMaxLtv() {
                        LtvEvaluationRequest req = LtvEvaluationRequest.builder()
                                        .occupationGroup(CustomerOccupationGroup.TECH_DRIVER)
                                        .category(CollateralCategory.MOTORBIKE)
                                        .appraisedValue(new BigDecimal("50000000"))
                                        .requestedLoanAmount(new BigDecimal("50000001")) // 50M + 1 VND
                                        .build();

                        BusinessException ex = assertThrows(BusinessException.class,
                                        () -> collateralService.evaluateLtv(req));
                        assertEquals(ErrorCode.EXCEEDS_MAX_LTV, ex.getErrorCode());
                }

                @Test
                @DisplayName("CollateralService.evaluateLtv: Zero or negative appraised value throws INVALID_REQUEST")
                void testEvaluateLtv_ZeroAppraisedValue_ThrowsInvalidRequest() {
                        LtvEvaluationRequest req = LtvEvaluationRequest.builder()
                                        .occupationGroup(CustomerOccupationGroup.TECH_DRIVER)
                                        .category(CollateralCategory.MOTORBIKE)
                                        .appraisedValue(BigDecimal.ZERO)
                                        .build();

                        BusinessException ex = assertThrows(BusinessException.class,
                                        () -> collateralService.evaluateLtv(req));
                        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
                }

                @Test
                @DisplayName("CollateralService.createCollateral: Automatically binds maxLtvPercent from LtvPolicyService")
                void testCreateCollateral_AutoBindsMaxLtvFromMatrix() {
                        when(customerRepository.findById(200L)).thenReturn(Optional.of(mockCustomer));
                        when(collateralRepository.save(any(CollateralEntity.class))).thenAnswer(inv -> {
                                CollateralEntity entity = inv.getArgument(0);
                                entity.setId(300L);
                                return entity;
                        });

                        CollateralCreateRequest req = CollateralCreateRequest.builder()
                                        .customerId(200L)
                                        .name("Honda SH 150i")
                                        .category(CollateralCategory.MOTORBIKE)
                                        .appraisedValue(new BigDecimal("80000000"))
                                        // maxLtvPercent omitted to test automatic matrix binding
                                        .build();

                        CollateralResponse resp = collateralService.createCollateral(req);

                        assertNotNull(resp);
                        // TECH_DRIVER x MOTORBIKE = 0.70
                        assertEquals(new BigDecimal("0.70"), resp.getMaxLtvPercent());
                        assertEquals(0, new BigDecimal("56000000.00").compareTo(resp.getMaxAllowedLoanAmount()));
                        verify(collateralRepository).save(any(CollateralEntity.class));
                }

                @Test
                @DisplayName("CustomerService.createCustomer: Rejects invalid verification document with INVALID_VERIFICATION_DOCUMENT")
                void testCustomerService_InvalidVerificationDocType_ThrowsInvalidVerificationDocument() {
                        when(customerRepository.existsByIdentityNumber(anyString())).thenReturn(false);

                        CustomerCreateRequest req = CustomerCreateRequest.builder()
                                        .fullName("Trần Thị Thử Nghiệm")
                                        .identityNumber("079095009999")
                                        .occupationGroup(CustomerOccupationGroup.TECH_DRIVER)
                                        // TECH_DRIVER requires DRIVER_APP_INCOME_SCREENSHOT,
                                        // RESIDENCE_CONFIRMATION_CT07, or RENTAL_CONTRACT
                                        .verificationDocType(VerificationDocumentType.PAYSLIP_BANK_STATEMENT) // Mismatched!
                                        .verificationDocUrls(List.of("https://cdn.pawnmaster.vn/payslip.jpg"))
                                        .build();

                        BusinessException ex = assertThrows(BusinessException.class,
                                        () -> customerService.createCustomer(req));
                        assertEquals(ErrorCode.INVALID_VERIFICATION_DOCUMENT, ex.getErrorCode());
                }

                @Test
                @DisplayName("CustomerService.createCustomer: Rejects empty verification document URLs with MISSING_VERIFICATION_DOCUMENTS")
                void testCustomerService_MissingVerificationDocUrls_ThrowsMissingVerificationDocuments() {
                        when(customerRepository.existsByIdentityNumber(anyString())).thenReturn(false);

                        CustomerCreateRequest req = CustomerCreateRequest.builder()
                                        .fullName("Trần Thị Thử Nghiệm")
                                        .identityNumber("079095009999")
                                        .occupationGroup(CustomerOccupationGroup.TECH_DRIVER)
                                        .verificationDocType(VerificationDocumentType.DRIVER_APP_INCOME_SCREENSHOT)
                                        .verificationDocUrls(Collections.emptyList()) // Empty!
                                        .build();

                        BusinessException ex = assertThrows(BusinessException.class,
                                        () -> customerService.createCustomer(req));
                        assertEquals(ErrorCode.MISSING_VERIFICATION_DOCUMENTS, ex.getErrorCode());
                }

                @Test
                @DisplayName("CustomerService.createCustomer: Rejects duplicate identity number with CUSTOMER_IDENTITY_EXISTS")
                void testCustomerService_DuplicateIdentity_ThrowsCustomerIdentityExists() {
                        when(customerRepository.existsByIdentityNumber("079090001234")).thenReturn(true);

                        CustomerCreateRequest req = CustomerCreateRequest.builder()
                                        .fullName("Trần Thị Thử Nghiệm")
                                        .identityNumber("079090001234")
                                        .occupationGroup(CustomerOccupationGroup.TECH_DRIVER)
                                        .verificationDocType(VerificationDocumentType.DRIVER_APP_INCOME_SCREENSHOT)
                                        .verificationDocUrls(List.of("https://cdn.pawnmaster.vn/driver.png"))
                                        .build();

                        BusinessException ex = assertThrows(BusinessException.class,
                                        () -> customerService.createCustomer(req));
                        assertEquals(ErrorCode.CUSTOMER_IDENTITY_EXISTS, ex.getErrorCode());
                }

                @Test
                @DisplayName("CustomerService.createCustomer: Successfully persists customer with valid matching documents")
                void testCustomerService_ValidCreation_Succeeds() {
                        when(customerRepository.existsByIdentityNumber(anyString())).thenReturn(false);
                        when(customerRepository.save(any(CustomerEntity.class))).thenAnswer(inv -> {
                                CustomerEntity entity = inv.getArgument(0);
                                entity.setId(500L);
                                return entity;
                        });

                        CustomerCreateRequest req = CustomerCreateRequest.builder()
                                        .fullName("Lê Văn Thành")
                                        .identityNumber("079095008888")
                                        .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                                        .verificationDocType(VerificationDocumentType.LABOR_CONTRACT)
                                        .verificationDocUrls(List.of("https://cdn.pawnmaster.vn/labor_contract.pdf"))
                                        .build();

                        CustomerResponse resp = customerService.createCustomer(req);

                        assertNotNull(resp);
                        assertEquals("Lê Văn Thành", resp.getFullName());
                        assertEquals(CustomerOccupationGroup.OFFICE_WORKER, resp.getOccupationGroup());
                        assertEquals(VerificationDocumentType.LABOR_CONTRACT, resp.getVerificationDocType());
                        verify(customerRepository).save(any(CustomerEntity.class));
                }
        }
}

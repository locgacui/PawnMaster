package com.pawnmaster.api.modules.collateral;

import com.pawnmaster.api.common.BaseResponse;
import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.collateral.dto.LtvEvaluationRequest;
import com.pawnmaster.api.modules.collateral.dto.LtvEvaluationResponse;
import com.pawnmaster.api.modules.contract.ContractEntity;
import com.pawnmaster.api.modules.contract.ContractStatus;
import com.pawnmaster.api.modules.contract.InterestType;
import com.pawnmaster.api.modules.customer.CustomerEntity;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.CustomerStatus;
import com.pawnmaster.api.modules.customer.VerificationDocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Challenger 2: Milestone 2 Whole-System Regression and Interface Verification Suite.
 *
 * Verifies:
 * 1. REST endpoint /api/v1/collaterals/evaluate-ltv behavior, response payload, and error conditions.
 * 2. M3 Interface Stability: CustomerEntity, CollateralEntity, and ContractEntity contract readiness.
 * 3. Contract Underwriting Simulation: Pre-flight LTV validation and Maker-Checker collateral lifecycle.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Challenger 2: Milestone 2 Regression & Interface Verification Suite")
public class Milestone2RegressionAndInterfaceTest {

    @Spy
    private LtvMatrixPolicy ltvMatrixPolicy = new LtvMatrixPolicy();

    @Mock
    private CollateralRepository collateralRepository;

    @InjectMocks
    private CollateralServiceImpl collateralService;

    private CollateralController collateralController;

    private BranchEntity branchQ1;
    private CustomerEntity officeCustomer;
    private CustomerEntity freelancerCustomer;
    private CollateralEntity motorbikeCollateral;

    @BeforeEach
    void setUp() {
        collateralController = new CollateralController(collateralService);

        branchQ1 = BranchEntity.builder()
                .branchCode("CN-Q1-001")
                .branchName("Chi nhánh Quận 1")
                .provinceCode("79")
                .districtCode("760")
                .capitalLimit(new BigDecimal("5000000000.00"))
                .currentCashBalance(new BigDecimal("1200000000.00"))
                .active(true)
                .build();
        branchQ1.setId(10L);

        officeCustomer = CustomerEntity.builder()
                .customerCode("KH-OFFICE-01")
                .fullName("Nguyễn Văn A")
                .identityNumber("079090001111")
                .phoneNumber("0901234567")
                .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                .verificationDocType(VerificationDocumentType.PAYSLIP_BANK_STATEMENT)
                .verificationDocUrls("https://cdn.pawnmaster.vn/docs/payslip.png")
                .branch(branchQ1)
                .status(CustomerStatus.ACTIVE)
                .build();
        officeCustomer.setId(101L);

        freelancerCustomer = CustomerEntity.builder()
                .customerCode("KH-FREE-02")
                .fullName("Trần Thị B")
                .identityNumber("079090002222")
                .phoneNumber("0907654321")
                .occupationGroup(CustomerOccupationGroup.FREELANCER)
                .verificationDocType(VerificationDocumentType.RESIDENCE_CONFIRMATION_CT07)
                .verificationDocUrls("https://cdn.pawnmaster.vn/docs/ct07.png")
                .branch(branchQ1)
                .status(CustomerStatus.ACTIVE)
                .build();
        freelancerCustomer.setId(102L);

        motorbikeCollateral = CollateralEntity.builder()
                .collateralCode("TS-MOTO-01")
                .customer(officeCustomer)
                .branch(branchQ1)
                .name("Honda Wave Alpha 110cc")
                .category(CollateralCategory.MOTORBIKE)
                .brand("Honda")
                .model("Wave Alpha")
                .serialOrFrameNumber("RLH1234567")
                .licensePlate("59-G2 123.45")
                .appraisedValue(new BigDecimal("20000000.00"))
                .maxLtvPercent(new BigDecimal("0.90"))
                .status(CollateralStatus.IN_VAULT)
                .build();
        motorbikeCollateral.setId(201L);
    }

    // =========================================================================
    // 1. REST ENDPOINT /api/v1/collaterals/evaluate-ltv VERIFICATION
    // =========================================================================
    @Nested
    @DisplayName("1. /api/v1/collaterals/evaluate-ltv Endpoint Behavior")
    class EvaluateLtvEndpointTests {

        @Test
        @DisplayName("Controller health check returns ready message")
        void testHealthCheck() {
            BaseResponse<String> response = collateralController.healthCheck();
            assertNotNull(response);
            assertTrue(response.isSuccess());
            assertEquals("Collateral module is ready", response.getData());
        }

        @Test
        @DisplayName("Controller categories returns all 8 collateral categories")
        void testGetCategories() {
            BaseResponse<List<java.util.Map<String, String>>> response = collateralController.getCategories();
            assertNotNull(response);
            assertTrue(response.isSuccess());
            assertEquals(8, response.getData().size());
        }

        @Test
        @DisplayName("evaluate-ltv without requested loan returns available max credit and 90% LTV for OFFICE_WORKER + MOTORBIKE")
        void testEvaluateLtv_NoRequestedLoan_ReturnsMaxCredit() {
            LtvEvaluationRequest request = LtvEvaluationRequest.builder()
                    .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                    .category(CollateralCategory.MOTORBIKE)
                    .appraisedValue(new BigDecimal("20000000.00"))
                    .build();

            BaseResponse<LtvEvaluationResponse> response = collateralController.evaluateLtv(request);

            assertNotNull(response);
            assertTrue(response.isSuccess());
            LtvEvaluationResponse data = response.getData();
            assertNotNull(data);
            assertEquals(CustomerOccupationGroup.OFFICE_WORKER, data.getOccupationGroup());
            assertEquals(CollateralCategory.MOTORBIKE, data.getCategory());
            assertEquals(new BigDecimal("0.90"), data.getMaxLtvPercent());
            assertEquals(0, new BigDecimal("18000000.00").compareTo(data.getMaxAllowedLoanAmount()));
            assertTrue(data.isEligible());
            assertTrue(data.getEligibleMessage().contains("18000000.00"));
        }

        @Test
        @DisplayName("evaluate-ltv with requested loan <= max allowed returns eligible = true")
        void testEvaluateLtv_WithinAllowedLimit_ReturnsEligibleTrue() {
            LtvEvaluationRequest request = LtvEvaluationRequest.builder()
                    .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                    .category(CollateralCategory.MOTORBIKE)
                    .appraisedValue(new BigDecimal("20000000.00"))
                    .requestedLoanAmount(new BigDecimal("15000000.00")) // 15M <= 18M
                    .build();

            BaseResponse<LtvEvaluationResponse> response = collateralController.evaluateLtv(request);

            assertNotNull(response);
            assertTrue(response.isSuccess());
            assertTrue(response.getData().isEligible());
        }

        @Test
        @DisplayName("evaluate-ltv with requested loan > max allowed but <= appraisedValue returns eligible = false with descriptive message")
        void testEvaluateLtv_ExceedsCategoryLimit_ReturnsEligibleFalse() {
            LtvEvaluationRequest request = LtvEvaluationRequest.builder()
                    .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                    .category(CollateralCategory.MOTORBIKE)
                    .appraisedValue(new BigDecimal("20000000.00"))
                    .requestedLoanAmount(new BigDecimal("19000000.00")) // 19M > 18M, but <= 20M
                    .build();

            BaseResponse<LtvEvaluationResponse> response = collateralController.evaluateLtv(request);

            assertNotNull(response);
            assertTrue(response.isSuccess());
            assertFalse(response.getData().isEligible());
            assertTrue(response.getData().getEligibleMessage().contains("vượt quá hạn mức tối đa cho phép"));
        }

        @Test
        @DisplayName("evaluate-ltv with requested loan > appraisedValue throws EXCEEDS_MAX_LTV")
        void testEvaluateLtv_ExceedsAppraisalCeiling_ThrowsExceedsMaxLtv() {
            LtvEvaluationRequest request = LtvEvaluationRequest.builder()
                    .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                    .category(CollateralCategory.MOTORBIKE)
                    .appraisedValue(new BigDecimal("20000000.00"))
                    .requestedLoanAmount(new BigDecimal("20000001.00")) // 20M + 1 VND
                    .build();

            BusinessException ex = assertThrows(BusinessException.class, () -> collateralController.evaluateLtv(request));
            assertEquals(ErrorCode.EXCEEDS_MAX_LTV, ex.getErrorCode());
            assertTrue(ex.getMessage().contains("vượt quá trần 100% giá trị định giá tài sản"));
        }

        @Test
        @DisplayName("evaluate-ltv with missing required parameters throws INVALID_REQUEST")
        void testEvaluateLtv_MissingParams_ThrowsInvalidRequest() {
            LtvEvaluationRequest request = LtvEvaluationRequest.builder()
                    .occupationGroup(null)
                    .category(CollateralCategory.MOTORBIKE)
                    .appraisedValue(new BigDecimal("20000000.00"))
                    .build();

            BusinessException ex = assertThrows(BusinessException.class, () -> collateralController.evaluateLtv(request));
            assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
        }
    }

    // =========================================================================
    // 2. INTERFACE STABILITY FOR UPCOMING MILESTONE 3
    // =========================================================================
    @Nested
    @DisplayName("2. Interface Stability: CustomerEntity & CollateralEntity Readiness for Contract Lifecycle")
    class InterfaceStabilityTests {

        @Test
        @DisplayName("CustomerEntity exposes all required accessors for Contract & Cashbook isolation")
        void testCustomerEntityInterfaceContracts() {
            assertEquals(10L, officeCustomer.getBranchId());
            assertEquals("CN-Q1-001", officeCustomer.getBranchCode());
            assertEquals("Chi nhánh Quận 1", officeCustomer.getBranchName());
            assertEquals(CustomerOccupationGroup.OFFICE_WORKER, officeCustomer.getOccupationGroup());
            assertEquals(CustomerStatus.ACTIVE, officeCustomer.getStatus());
            assertEquals(1, officeCustomer.getVerificationDocUrlList().size());
            assertEquals("https://cdn.pawnmaster.vn/docs/payslip.png", officeCustomer.getVerificationDocUrlList().get(0));

            // Verify setter contracts
            officeCustomer.setOccupationGroup(CustomerOccupationGroup.SMALL_MERCHANT);
            assertEquals(CustomerOccupationGroup.SMALL_MERCHANT, officeCustomer.getOccupationGroup());
        }

        @Test
        @DisplayName("CollateralEntity exposes all required accessors for Contract, Vault & Cashbook")
        void testCollateralEntityInterfaceContracts() {
            assertEquals(10L, motorbikeCollateral.getBranchId());
            assertEquals("CN-Q1-001", motorbikeCollateral.getBranchCode());
            assertEquals("Chi nhánh Quận 1", motorbikeCollateral.getBranchName());
            assertEquals(101L, motorbikeCollateral.getCustomerId());
            assertEquals("Nguyễn Văn A", motorbikeCollateral.getCustomerName());
            assertEquals(new BigDecimal("20000000.00"), motorbikeCollateral.getAppraisedValue());
            assertEquals(new BigDecimal("0.90"), motorbikeCollateral.getMaxLtvPercent());
            assertEquals(CollateralStatus.IN_VAULT, motorbikeCollateral.getStatus());

            // Test lifecycle transitions required by M3 Maker-Checker
            motorbikeCollateral.setStatus(CollateralStatus.REDEEMED);
            assertEquals(CollateralStatus.REDEEMED, motorbikeCollateral.getStatus());

            motorbikeCollateral.setStatus(CollateralStatus.WAITING_LIQUIDATION);
            assertEquals(CollateralStatus.WAITING_LIQUIDATION, motorbikeCollateral.getStatus());

            motorbikeCollateral.setStatus(CollateralStatus.LIQUIDATED);
            assertEquals(CollateralStatus.LIQUIDATED, motorbikeCollateral.getStatus());
        }

        @Test
        @DisplayName("CollateralStatus enum contains exactly the 4 required M3 lifecycle states")
        void testCollateralStatusEnumCompleteness() {
            List<String> statusNames = Arrays.stream(CollateralStatus.values())
                    .map(Enum::name)
                    .toList();

            assertTrue(statusNames.contains("IN_VAULT"));
            assertTrue(statusNames.contains("WAITING_LIQUIDATION"));
            assertTrue(statusNames.contains("REDEEMED"));
            assertTrue(statusNames.contains("LIQUIDATED"));
            assertEquals(4, statusNames.size());
        }

        @Test
        @DisplayName("ContractEntity can link CustomerEntity and CollateralEntity seamlessly")
        void testContractEntityBindingSimulation() {
            ContractEntity contract = ContractEntity.builder()
                    .contractCode("HD-202611-0001")
                    .customer(officeCustomer)
                    .collateral(motorbikeCollateral)
                    .principalAmount(new BigDecimal("15000000.00"))
                    .remainingPrincipal(new BigDecimal("15000000.00"))
                    .interestType(InterestType.DAILY_PER_MILLION)
                    .interestRate(new BigDecimal("1500.0000")) // 1,500 VND/million/day
                    .storageFeeRate(new BigDecimal("0.0000"))
                    .paymentCycleDays(10)
                    .startDate(LocalDate.of(2026, 11, 10))
                    .maturityDate(LocalDate.of(2026, 12, 10))
                    .nextInterestDueDate(LocalDate.of(2026, 11, 20))
                    .status(ContractStatus.ACTIVE)
                    .build();
            contract.setId(501L);

            // M3 Integrity assertions:
            assertNotNull(contract.getCustomer());
            assertEquals(officeCustomer.getId(), contract.getCustomer().getId());
            assertNotNull(contract.getCollateral());
            assertEquals(motorbikeCollateral.getId(), contract.getCollateral().getId());

            // Branch alignment check: Customer and Collateral must belong to the same branch
            assertEquals(contract.getCustomer().getBranchId(), contract.getCollateral().getBranchId());

            // 100% hard ceiling rule check
            assertTrue(contract.getPrincipalAmount().compareTo(contract.getCollateral().getAppraisedValue()) <= 0);

            // Matrix LTV ceiling check (15M <= 20M * 0.90 = 18M)
            BigDecimal maxAllowedLoan = contract.getCollateral().getAppraisedValue()
                    .multiply(contract.getCollateral().getMaxLtvPercent())
                    .setScale(2, RoundingMode.HALF_UP);
            assertTrue(contract.getPrincipalAmount().compareTo(maxAllowedLoan) <= 0);

            // Contract Status initially ACTIVE in current M2 schema (PENDING_APPROVAL to be added in M3)
            contract.setStatus(ContractStatus.ACTIVE);
            assertEquals(ContractStatus.ACTIVE, contract.getStatus());

            // Simulate redemption lifecycle:
            contract.setStatus(ContractStatus.REDEEMED);
            motorbikeCollateral.setStatus(CollateralStatus.REDEEMED);
            assertEquals(ContractStatus.REDEEMED, contract.getStatus());
            assertEquals(CollateralStatus.REDEEMED, motorbikeCollateral.getStatus());
        }
    }

    // =========================================================================
    // 3. COMPREHENSIVE OCCUPATION GROUPS × COLLATERAL CATEGORIES MATRIX CHECK
    // =========================================================================
    @Nested
    @DisplayName("3. Matrix Underwriting Completeness Across All Groups")
    class UnderwritingMatrixTests {

        @ParameterizedTest
        @EnumSource(CustomerOccupationGroup.class)
        @DisplayName("All 6 Occupation Groups evaluate correctly for MOTORBIKE")
        void testAllGroupsForMotorbike(CustomerOccupationGroup group) {
            BigDecimal appraisedValue = new BigDecimal("50000000.00");
            BigDecimal ltvRatio = ltvMatrixPolicy.getMaxLtvPercent(group, CollateralCategory.MOTORBIKE);
            BigDecimal maxLoan = ltvMatrixPolicy.calculateMaxLoanAmount(group, CollateralCategory.MOTORBIKE, appraisedValue);

            assertNotNull(ltvRatio);
            assertTrue(ltvRatio.compareTo(BigDecimal.ZERO) > 0);
            assertTrue(ltvRatio.compareTo(BigDecimal.ONE) <= 0);
            assertEquals(0, appraisedValue.multiply(ltvRatio).setScale(2, RoundingMode.HALF_UP).compareTo(maxLoan));
        }

        @ParameterizedTest
        @EnumSource(CollateralCategory.class)
        @DisplayName("All 8 Collateral Categories evaluate correctly for FREELANCER")
        void testAllCategoriesForFreelancer(CollateralCategory category) {
            BigDecimal appraisedValue = new BigDecimal("100000000.00");
            BigDecimal ltvRatio = ltvMatrixPolicy.getMaxLtvPercent(CustomerOccupationGroup.FREELANCER, category);
            BigDecimal maxLoan = ltvMatrixPolicy.calculateMaxLoanAmount(CustomerOccupationGroup.FREELANCER, category, appraisedValue);

            assertNotNull(ltvRatio);
            assertTrue(ltvRatio.compareTo(BigDecimal.ZERO) > 0);
            assertTrue(ltvRatio.compareTo(BigDecimal.ONE) <= 0);
            assertEquals(0, appraisedValue.multiply(ltvRatio).setScale(2, RoundingMode.HALF_UP).compareTo(maxLoan));
        }
    }
}

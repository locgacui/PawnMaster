package com.pawnmaster.api.modules.collateral;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.branch.BranchRepository;
import com.pawnmaster.api.modules.collateral.dto.CollateralCreateRequest;
import com.pawnmaster.api.modules.collateral.dto.CollateralResponse;
import com.pawnmaster.api.modules.collateral.dto.CollateralUpdateRequest;
import com.pawnmaster.api.modules.collateral.dto.LtvEvaluationRequest;
import com.pawnmaster.api.modules.collateral.dto.LtvEvaluationResponse;
import com.pawnmaster.api.modules.customer.CustomerEntity;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.CustomerRepository;
import com.pawnmaster.api.modules.customer.VerificationDocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Checkpoint 2: CollateralService Unit Tests")
public class CollateralServiceTest {

    @Mock
    private CollateralRepository collateralRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private BranchRepository branchRepository;

    @Spy
    private LtvPolicyService ltvPolicyService = LtvPolicyTestHelper.createDefaultPolicyService();

    @InjectMocks
    private CollateralServiceImpl collateralService;

    private CustomerEntity sampleCustomer;
    private BranchEntity sampleBranch;
    private CollateralEntity sampleCollateral;
    private CollateralCreateRequest createRequest;

    @BeforeEach
    void setUp() {
        sampleBranch = BranchEntity.builder()
                .branchCode("CN-HCM-01")
                .branchName("Chi nhánh Quận 1")
                .provinceCode("79")
                .districtCode("760")
                .capitalLimit(new BigDecimal("2500000000.00"))
                .currentCashBalance(new BigDecimal("500000000.00"))
                .active(true)
                .build();
        sampleBranch.setId(1L);

        sampleCustomer = CustomerEntity.builder()
                .customerCode("KH-0001")
                .fullName("Nguyễn Văn A")
                .identityNumber("079090123456")
                .phoneNumber("0901234567")
                .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                .verificationDocType(VerificationDocumentType.PAYSLIP_BANK_STATEMENT)
                .verificationDocUrls("https://s3.aws.com/pawn/docs/slip1.png")
                .build();
        sampleCustomer.setId(100L);

        sampleCollateral = CollateralEntity.builder()
                .collateralCode("TS-0001")
                .customer(sampleCustomer)
                .name("Honda SH 150i ABS 2023")
                .category(CollateralCategory.MOTORBIKE)
                .brand("Honda")
                .model("SH 150i")
                .serialOrFrameNumber("RLH123456789")
                .licensePlate("59-A1 999.99")
                .conditionDescription("Mới 95%, nguyên bản, đủ 2 chìa smartkey")
                .appraisedValue(new BigDecimal("80000000.00"))
                .storageLocation("Kho xe tầng hầm - Kệ A01")
                .imageUrls("https://s3.aws.com/pawn/collateral/sh1.jpg")
                .status(CollateralStatus.IN_VAULT)
                .build();
        sampleCollateral.setId(200L);

        createRequest = CollateralCreateRequest.builder()
                .customerId(100L)
                .branchId(1L)
                .name("Honda SH 150i ABS 2023")
                .category(CollateralCategory.MOTORBIKE)
                .brand("Honda")
                .model("SH 150i")
                .serialOrFrameNumber("RLH123456789")
                .licensePlate("59-A1 999.99")
                .conditionDescription("Mới 95%, nguyên bản, đủ 2 chìa smartkey")
                .appraisedValue(new BigDecimal("80000000.00"))
                .storageLocation("Kho xe tầng hầm - Kệ A01")
                .imageUrls("https://s3.aws.com/pawn/collateral/sh1.jpg")
                .build();
    }

    // =========================================================================
    // 1. COLLATERAL CREATION & APPRAISAL TESTS
    // =========================================================================

    @Test
    @DisplayName("Create collateral: Success, calculates LTV percent (90% for Office Worker + Motorbike) and saves")
    void createCollateral_Success_CalculatesLtvAndSaves() {
        when(customerRepository.findById(100L)).thenReturn(Optional.of(sampleCustomer));
        when(branchRepository.findById(1L)).thenReturn(Optional.of(sampleBranch));
        when(collateralRepository.save(any(CollateralEntity.class))).thenAnswer(invocation -> {
            CollateralEntity entity = invocation.getArgument(0);
            entity.setId(200L);
            return entity;
        });

        CollateralResponse response = collateralService.createCollateral(createRequest);

        assertNotNull(response);
        assertEquals("Honda SH 150i ABS 2023", response.getName());
        assertEquals(CollateralCategory.MOTORBIKE, response.getCategory());
        assertEquals(0, new BigDecimal("80000000.00").compareTo(response.getAppraisedValue()));
        assertEquals(CollateralStatus.IN_VAULT, response.getStatus());

        // Office worker + Motorbike = 90% LTV -> max allowed loan is 72,000,000
        assertNotNull(response.getMaxLtvPercent());
        assertNotNull(response.getMaxAllowedLoanAmount());
        assertEquals(0, new BigDecimal("72000000.00").compareTo(response.getMaxAllowedLoanAmount()));

        verify(collateralRepository, times(1)).save(any(CollateralEntity.class));
    }

    @Test
    @DisplayName("Create collateral: Customer not found throws CUSTOMER_NOT_FOUND")
    void createCollateral_CustomerNotFound_ThrowsCustomerNotFound() {
        when(customerRepository.findById(100L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> collateralService.createCollateral(createRequest));

        assertEquals(ErrorCode.CUSTOMER_NOT_FOUND, ex.getErrorCode());
        verify(collateralRepository, never()).save(any());
    }

    @Test
    @DisplayName("Create collateral: Branch not found throws BRANCH_NOT_FOUND")
    void createCollateral_BranchNotFound_ThrowsBranchNotFound() {
        when(customerRepository.findById(100L)).thenReturn(Optional.of(sampleCustomer));
        when(branchRepository.findById(1L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> collateralService.createCollateral(createRequest));

        assertEquals(ErrorCode.BRANCH_NOT_FOUND, ex.getErrorCode());
        verify(collateralRepository, never()).save(any());
    }

    // =========================================================================
    // 2. FAST LTV EVALUATION ENDPOINT TESTS (/api/v1/collaterals/evaluate-ltv)
    // =========================================================================

    @Test
    @DisplayName("Fast LTV Evaluation: Within limits returns eligible true")
    void evaluateLtv_WithinLimits_ReturnsEligibleTrue() {
        LtvEvaluationRequest request = LtvEvaluationRequest.builder()
                .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                .category(CollateralCategory.MOTORBIKE)
                .appraisedValue(new BigDecimal("100000000.00")) // 100 tr
                .requestedLoanAmount(new BigDecimal("85000000.00")) // 85 tr <= 90 tr (90%)
                .build();

        LtvEvaluationResponse response = collateralService.evaluateLtv(request);

        assertNotNull(response);
        assertEquals(CustomerOccupationGroup.OFFICE_WORKER, response.getOccupationGroup());
        assertEquals(CollateralCategory.MOTORBIKE, response.getCategory());
        assertEquals(0, new BigDecimal("100000000.00").compareTo(response.getAppraisedValue()));
        assertEquals(0, new BigDecimal("90000000.00").compareTo(response.getMaxAllowedLoanAmount()));
        assertTrue(response.isEligible(), "85M requested should be eligible under 90M max limit");
    }

    @Test
    @DisplayName("Fast LTV Evaluation: Exceeds category limit returns eligible false")
    void evaluateLtv_ExceedsCategoryLimit_ReturnsEligibleFalse() {
        LtvEvaluationRequest request = LtvEvaluationRequest.builder()
                .occupationGroup(CustomerOccupationGroup.FREELANCER) // Freelancer LTV for Motorbike is 65%
                .category(CollateralCategory.MOTORBIKE)
                .appraisedValue(new BigDecimal("100000000.00")) // 100 tr
                .requestedLoanAmount(new BigDecimal("70000000.00")) // 70 tr > 65 tr (65%)
                .build();

        LtvEvaluationResponse response = collateralService.evaluateLtv(request);

        assertNotNull(response);
        assertEquals(0, new BigDecimal("65000000.00").compareTo(response.getMaxAllowedLoanAmount()));
        assertFalse(response.isEligible(), "70M requested should not be eligible under 65M max limit for Freelancer");
    }

    @Test
    @DisplayName("Fast LTV Evaluation: Exceeds 100% of appraised value throws EXCEEDS_MAX_LTV")
    void evaluateLtv_Exceeds100PercentCeiling_ThrowsExceedsMaxLtv() {
        LtvEvaluationRequest request = LtvEvaluationRequest.builder()
                .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                .category(CollateralCategory.GOLD_JEWELRY)
                .appraisedValue(new BigDecimal("50000000.00"))
                .requestedLoanAmount(new BigDecimal("55000000.00")) // 110% of appraised value!
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> collateralService.evaluateLtv(request));

        assertEquals(ErrorCode.EXCEEDS_MAX_LTV, ex.getErrorCode());
    }

    @Test
    @DisplayName("Fast LTV Evaluation: Policy missing in database throws BusinessException with INVALID_REQUEST")
    void evaluateLtv_PolicyNotFoundInDatabase_ThrowsBusinessException() {
        LtvEvaluationRequest request = LtvEvaluationRequest.builder()
                .occupationGroup(CustomerOccupationGroup.FREELANCER)
                .category(CollateralCategory.WATCH)
                .appraisedValue(new BigDecimal("10000000.00"))
                .requestedLoanAmount(new BigDecimal("5000000.00"))
                .build();

        doThrow(new BusinessException(ErrorCode.INVALID_REQUEST, "Không tìm thấy cấu hình LTV"))
                .when(ltvPolicyService).calculateMaxLoanAmount(eq(CustomerOccupationGroup.FREELANCER), eq(CollateralCategory.WATCH), any());

        BusinessException ex = assertThrows(BusinessException.class, () -> collateralService.evaluateLtv(request));
        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Không tìm thấy cấu hình LTV"));
    }

    @Test
    @DisplayName("Fast LTV Evaluation: Dynamic policy update reflects new ceiling dynamically without restart")
    void evaluateLtv_DynamicDatabaseGovernance_ScalesLimitDirectly() {
        LtvEvaluationRequest request = LtvEvaluationRequest.builder()
                .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                .category(CollateralCategory.MOTORBIKE)
                .appraisedValue(new BigDecimal("100000000.00"))
                .requestedLoanAmount(new BigDecimal("80000000.00"))
                .build();

        // Standard default is 90% (90M), so 80M is eligible
        LtvEvaluationResponse initialResp = collateralService.evaluateLtv(request);
        assertTrue(initialResp.isEligible());
        assertEquals(0, new BigDecimal("90000000.00").compareTo(initialResp.getMaxAllowedLoanAmount()));

        // Dynamically tighten LTV policy to 70% (0.70)
        doReturn(new BigDecimal("0.70"))
                .when(ltvPolicyService).getMaxLtvRatio(CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.MOTORBIKE);
        doReturn(new BigDecimal("70000000.00"))
                .when(ltvPolicyService).calculateMaxLoanAmount(CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.MOTORBIKE, new BigDecimal("100000000.00"));

        LtvEvaluationResponse updatedResp = collateralService.evaluateLtv(request);
        assertFalse(updatedResp.isEligible(), "80M requested should no longer be eligible under tightened 70M policy");
        assertEquals(0, new BigDecimal("70000000.00").compareTo(updatedResp.getMaxAllowedLoanAmount()));
    }

    // =========================================================================
    // 3. COLLATERAL RETRIEVAL & STORAGE LOCATION UPDATE TESTS
    // =========================================================================

    @Test
    @DisplayName("Get collateral by ID: Success")
    void getCollateralById_Success() {
        when(collateralRepository.findById(200L)).thenReturn(Optional.of(sampleCollateral));

        CollateralResponse response = collateralService.getCollateralById(200L);

        assertNotNull(response);
        assertEquals(200L, response.getId());
        assertEquals("TS-0001", response.getCollateralCode());
        assertEquals("Honda SH 150i ABS 2023", response.getName());
    }

    @Test
    @DisplayName("Get collateral by ID: Not found throws COLLATERAL_NOT_FOUND")
    void getCollateralById_NotFound_ThrowsCollateralNotFound() {
        when(collateralRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> collateralService.getCollateralById(999L));

        assertEquals(ErrorCode.COLLATERAL_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Get collaterals by customer ID: Returns list")
    void getCollateralsByCustomerId_ReturnsList() {
        when(collateralRepository.findByCustomerIdAndDeletedFalse(100L)).thenReturn(List.of(sampleCollateral));

        List<CollateralResponse> list = collateralService.getCollateralsByCustomerId(100L);

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("TS-0001", list.get(0).getCollateralCode());
    }

    @Test
    @DisplayName("Update storage location: Success updates vault location")
    void updateCollateralStorageLocation_Success() {
        when(collateralRepository.findById(200L)).thenReturn(Optional.of(sampleCollateral));

        CollateralUpdateRequest updateRequest = CollateralUpdateRequest.builder()
                .storageLocation("Kho xe VIP - Kệ VIP-01")
                .conditionDescription("Đã bảo dưỡng định kỳ trong kho")
                .build();

        CollateralResponse response = collateralService.updateCollateral(200L, updateRequest);

        assertNotNull(response);
        assertEquals("Kho xe VIP - Kệ VIP-01", response.getStorageLocation());
    }
}

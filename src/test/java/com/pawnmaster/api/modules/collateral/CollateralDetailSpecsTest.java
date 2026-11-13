package com.pawnmaster.api.modules.collateral;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.branch.BranchRepository;
import com.pawnmaster.api.modules.collateral.detail.CollateralDetailManager;
import com.pawnmaster.api.modules.collateral.detail.CollateralGoldDetailEntity;
import com.pawnmaster.api.modules.collateral.detail.CollateralGoldDetailRepository;
import com.pawnmaster.api.modules.collateral.detail.CollateralOtherDetailEntity;
import com.pawnmaster.api.modules.collateral.detail.CollateralOtherDetailRepository;
import com.pawnmaster.api.modules.collateral.detail.CollateralTechDetailEntity;
import com.pawnmaster.api.modules.collateral.detail.CollateralTechDetailRepository;
import com.pawnmaster.api.modules.collateral.detail.CollateralVehicleDetailEntity;
import com.pawnmaster.api.modules.collateral.detail.CollateralVehicleDetailRepository;
import com.pawnmaster.api.modules.collateral.dto.CollateralCreateRequest;
import com.pawnmaster.api.modules.collateral.dto.CollateralResponse;
import com.pawnmaster.api.modules.collateral.dto.CollateralUpdateRequest;
import com.pawnmaster.api.modules.contract.ContractEntity;
import com.pawnmaster.api.modules.contract.ContractStatus;
import com.pawnmaster.api.modules.contract.InterestType;
import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import com.pawnmaster.api.modules.customer.CustomerEntity;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.CustomerRepository;
import com.pawnmaster.api.modules.customer.VerificationDocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Normalized Collateral 1:1 Detail Tables Tests")
class CollateralDetailSpecsTest {

    @Mock
    private CollateralRepository collateralRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private CollateralVehicleDetailRepository vehicleDetailRepository;

    @Mock
    private CollateralTechDetailRepository techDetailRepository;

    @Mock
    private CollateralGoldDetailRepository goldDetailRepository;

    @Mock
    private CollateralOtherDetailRepository otherDetailRepository;

    private CollateralDetailManager detailManager;
    private CollateralServiceImpl collateralService;

    private CustomerEntity sampleCustomer;
    private BranchEntity sampleBranch;

    @BeforeEach
    void setUp() {
        detailManager = new CollateralDetailManager(
                vehicleDetailRepository,
                techDetailRepository,
                goldDetailRepository,
                otherDetailRepository);
        LtvPolicyService ltvPolicyService = LtvPolicyTestHelper.createDefaultPolicyService();
        collateralService = new CollateralServiceImpl(
                collateralRepository,
                customerRepository,
                branchRepository,
                ltvPolicyService,
                detailManager);

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
                .identityNumber("079099111222")
                .phoneNumber("0909123456")
                .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                .verificationDocType(VerificationDocumentType.PAYSLIP_BANK_STATEMENT)
                .build();
        sampleCustomer.setId(10L);
    }

    @Test
    @DisplayName("Vehicle: persists ODO & RegNumber in collateral_vehicle_details and enriches response")
    void createVehicleCollateral_persistsNormalizedVehicleDetail() {
        when(customerRepository.findById(10L)).thenReturn(Optional.of(sampleCustomer));
        when(branchRepository.findById(1L)).thenReturn(Optional.of(sampleBranch));
        when(collateralRepository.save(any(CollateralEntity.class))).thenAnswer(inv -> {
            CollateralEntity e = inv.getArgument(0);
            e.setId(101L);
            return e;
        });
        when(vehicleDetailRepository.findById(101L)).thenReturn(Optional.empty());
        when(vehicleDetailRepository.save(any(CollateralVehicleDetailEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(vehicleDetailRepository.findAllById(any())).thenReturn(List.of(
                CollateralVehicleDetailEntity.builder()
                        .collateralId(101L)
                        .licensePlate("59-P1 988.22")
                        .frameNumber("RLHJF8209PZ104821")
                        .registrationNumber("DKX-79-0084512")
                        .odometerKm(8450)
                        .build()));

        CollateralCreateRequest req = CollateralCreateRequest.builder()
                .customerId(10L)
                .branchId(1L)
                .name("Honda SH 150i ABS")
                .category(CollateralCategory.MOTORBIKE)
                .licensePlate("59-P1 988.22")
                .vehicleFrameNumber("RLHJF8209PZ104821")
                .vehicleRegNumber("DKX-79-0084512")
                .vehicleOdometerKm(8450)
                .appraisedValue(new BigDecimal("85000000"))
                .build();

        CollateralResponse res = collateralService.createCollateral(req);

        ArgumentCaptor<CollateralVehicleDetailEntity> captor =
                ArgumentCaptor.forClass(CollateralVehicleDetailEntity.class);
        verify(vehicleDetailRepository).save(captor.capture());
        assertEquals(101L, captor.getValue().getCollateralId());
        assertEquals(8450, captor.getValue().getOdometerKm());
        assertEquals("DKX-79-0084512", captor.getValue().getRegistrationNumber());
        assertEquals("RLHJF8209PZ104821", res.getSerialOrFrameNumber());
        assertEquals(8450, res.getVehicleOdometerKm());
        assertEquals("DKX-79-0084512", res.getVehicleRegNumber());
    }

    @Test
    @DisplayName("Vehicle: negative ODO rejects request with BusinessException")
    void createVehicleCollateral_negativeOdometer_throwsException() {
        when(customerRepository.findById(10L)).thenReturn(Optional.of(sampleCustomer));
        when(branchRepository.findById(1L)).thenReturn(Optional.of(sampleBranch));

        CollateralCreateRequest req = CollateralCreateRequest.builder()
                .customerId(10L)
                .branchId(1L)
                .name("Toyota Vios")
                .category(CollateralCategory.CAR)
                .licensePlate("51H-678.90")
                .vehicleFrameNumber("RL4B29F31M0056789")
                .vehicleOdometerKm(-10)
                .appraisedValue(new BigDecimal("420000000"))
                .build();

        assertThrows(BusinessException.class, () -> collateralService.createCollateral(req));
        verify(collateralRepository, never()).save(any());
    }

    @Test
    @DisplayName("Gold: enforces purity & netWeightChi > 0 and syncs summary onto parent")
    void createGoldCollateral_validationAndPersistence() {
        when(customerRepository.findById(10L)).thenReturn(Optional.of(sampleCustomer));
        when(branchRepository.findById(1L)).thenReturn(Optional.of(sampleBranch));

        CollateralCreateRequest missingWeight = CollateralCreateRequest.builder()
                .customerId(10L)
                .branchId(1L)
                .name("Nhẫn trơn SJC 24K")
                .category(CollateralCategory.GOLD_JEWELRY)
                .goldPurity("9999")
                .appraisedValue(new BigDecimal("25000000"))
                .build();
        assertThrows(BusinessException.class, () -> collateralService.createCollateral(missingWeight));

        when(collateralRepository.save(any(CollateralEntity.class))).thenAnswer(inv -> {
            CollateralEntity e = inv.getArgument(0);
            e.setId(202L);
            return e;
        });
        when(goldDetailRepository.findById(202L)).thenReturn(Optional.empty());
        when(goldDetailRepository.save(any(CollateralGoldDetailEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(goldDetailRepository.findAllById(any())).thenReturn(List.of(
                CollateralGoldDetailEntity.builder()
                        .collateralId(202L)
                        .goldPurity("9999")
                        .netWeightChi(new BigDecimal("5.250"))
                        .build()));

        CollateralCreateRequest validGold = CollateralCreateRequest.builder()
                .customerId(10L)
                .branchId(1L)
                .name("Nhẫn trơn SJC 24K")
                .category(CollateralCategory.GOLD_JEWELRY)
                .goldPurity("9999")
                .goldWeightChi(new BigDecimal("5.250"))
                .appraisedValue(new BigDecimal("42000000"))
                .build();

        CollateralResponse res = collateralService.createCollateral(validGold);
        assertEquals("Vàng 9999 - 5.25 chỉ", res.getSerialOrFrameNumber());
        assertEquals("9999", res.getGoldPurity());
        assertEquals(0, new BigDecimal("5.250").compareTo(res.getGoldWeightChi()));
    }

    @Test
    @DisplayName("Category change on update removes old vehicle row and saves new tech row")
    void updateCollateral_categorySwitch_cleansOldDetailTable() {
        CollateralEntity existing = CollateralEntity.builder()
                .collateralCode("TS-2611-B01-XM0001")
                .customer(sampleCustomer)
                .branch(sampleBranch)
                .name("Xe máy cũ")
                .category(CollateralCategory.MOTORBIKE)
                .licensePlate("59-P1 988.22")
                .serialOrFrameNumber("FRAME-01")
                .appraisedValue(new BigDecimal("30000000"))
                .status(CollateralStatus.IN_VAULT)
                .build();
        existing.setId(303L);

        when(collateralRepository.findById(303L)).thenReturn(Optional.of(existing));
        when(vehicleDetailRepository.existsById(303L)).thenReturn(true);
        when(techDetailRepository.findById(303L)).thenReturn(Optional.empty());
        when(techDetailRepository.save(any(CollateralTechDetailEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(techDetailRepository.findAllById(any())).thenReturn(List.of(
                CollateralTechDetailEntity.builder()
                        .collateralId(303L)
                        .imeiOrSerial("358921004899123")
                        .passcodeCloudStatus("ICLOUD_OFF_UNLOCKED")
                        .accessories("Hộp zin + sạc cáp")
                        .build()));

        CollateralUpdateRequest updateReq = CollateralUpdateRequest.builder()
                .category(CollateralCategory.PHONE)
                .techImeiOrSerial("358921004899123")
                .techPasscodeStatus("ICLOUD_OFF_UNLOCKED")
                .techAccessories("Hộp zin + sạc cáp")
                .build();

        CollateralResponse updated = collateralService.updateCollateral(303L, updateReq);

        verify(vehicleDetailRepository).deleteById(303L);
        verify(techDetailRepository).save(any(CollateralTechDetailEntity.class));
        assertNull(updated.getLicensePlate());
        assertEquals("358921004899123", updated.getSerialOrFrameNumber());
        assertEquals("358921004899123", updated.getTechImeiOrSerial());
        assertEquals("Hộp zin + sạc cáp", updated.getTechAccessories());
    }

    @Test
    @DisplayName("Batch ContractResponse enrichment populates detail specs including OTHER assets")
    void enrichContractResponses_populatesOtherAndGoldDetailsInBatch() {
        CollateralEntity otherAsset = CollateralEntity.builder()
                .collateralCode("TS-2611-B01-KH0009")
                .customer(sampleCustomer)
                .branch(sampleBranch)
                .name("Máy ảnh Sony A7 IV")
                .brand("Sony")
                .model("ILCE-7M4")
                .category(CollateralCategory.OTHER)
                .serialOrFrameNumber("SN-SONY-A7M4-8812")
                .appraisedValue(new BigDecimal("48000000"))
                .status(CollateralStatus.IN_VAULT)
                .build();
        otherAsset.setId(404L);

        ContractEntity contract = ContractEntity.builder()
                .contractCode("HD-2611-B01-000404")
                .branch(sampleBranch)
                .customer(sampleCustomer)
                .collateral(otherAsset)
                .principalAmount(new BigDecimal("28000000"))
                .remainingPrincipal(new BigDecimal("28000000"))
                .interestType(InterestType.DAILY_PER_MILLION)
                .interestRate(new BigDecimal("1800"))
                .loanTermDays(30)
                .paymentCycleDays(10)
                .startDate(LocalDate.now())
                .maturityDate(LocalDate.now().plusDays(30))
                .nextInterestDueDate(LocalDate.now().plusDays(10))
                .status(ContractStatus.ACTIVE)
                .build();
        contract.setId(504L);

        when(otherDetailRepository.findAllById(any())).thenReturn(List.of(
                CollateralOtherDetailEntity.builder()
                        .collateralId(404L)
                        .detailedSpec("SN-SONY-A7M4-8812 - Kèm ống kính 24-70mm GM II")
                        .build()));

        ContractResponse resp = ContractResponse.fromEntity(contract);
        detailManager.enrichContractResponses(List.of(resp));

        assertEquals("Sony", resp.getCollateralBrand());
        assertEquals("ILCE-7M4", resp.getCollateralModel());
        assertEquals("SN-SONY-A7M4-8812 - Kèm ống kính 24-70mm GM II", resp.getCollateralOtherAssetSpec());
        verifyNoInteractions(vehicleDetailRepository, techDetailRepository, goldDetailRepository);
    }
}

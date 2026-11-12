package com.pawnmaster.api.modules.contract;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.branch.BranchRepository;
import com.pawnmaster.api.modules.branch.DistrictRepository;
import com.pawnmaster.api.modules.branch.ProvinceRepository;
import com.pawnmaster.api.modules.branch.WardRepository;
import com.pawnmaster.api.modules.cashbook.CashbookService;
import com.pawnmaster.api.modules.cashbook.TransactionCategory;
import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.collateral.CollateralEntity;
import com.pawnmaster.api.modules.collateral.CollateralRepository;
import com.pawnmaster.api.modules.collateral.CollateralStatus;
import com.pawnmaster.api.modules.collateral.LtvPolicyService;
import com.pawnmaster.api.modules.contract.dto.ContractRedeemRequest;
import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import com.pawnmaster.api.modules.contract.dto.InterestCalculationResponse;
import com.pawnmaster.api.modules.contract.dto.InterestCollectionRequest;
import com.pawnmaster.api.modules.contract.dto.InterestPaymentResponse;
import com.pawnmaster.api.modules.contract.dto.QuickContractOriginationRequest;
import com.pawnmaster.api.modules.customer.CustomerEntity;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.CustomerRepository;
import com.pawnmaster.api.modules.customer.CustomerStatus;
import com.pawnmaster.api.modules.customer.VerificationDocumentPolicy;
import com.pawnmaster.api.modules.customer.VerificationDocumentType;
import com.pawnmaster.api.modules.customer.dto.CustomerCreateRequest;
import com.pawnmaster.api.modules.customer.dto.CustomerResponse;
import com.pawnmaster.api.modules.customer.dto.CustomerUpdateRequest;
import com.pawnmaster.api.modules.user.RoleEnum;
import com.pawnmaster.api.modules.user.UserEntity;
import com.pawnmaster.api.modules.user.UserRepository;
import com.pawnmaster.api.security.BranchSecurityGuard;
import com.pawnmaster.api.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phase 4: Global Branch-Agnostic Customer, Cross-Branch Interest Servicing & Strict Vault Redemption Gate")
class CustomerBranchDecouplingAndRedemptionTest {

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CollateralRepository collateralRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CashbookService cashbookService;

    @Mock
    private LtvPolicyService ltvPolicyService;

    @Mock
    private InterestPaymentRepository interestPaymentRepository;

    @Mock
    private ProvinceRepository provinceRepository;

    @Mock
    private DistrictRepository districtRepository;

    @Mock
    private WardRepository wardRepository;

    private BranchSecurityGuard branchSecurityGuard;
    private VerificationDocumentPolicy verificationDocumentPolicy;
    private ContractServiceImpl contractService;

    private BranchEntity branch1Hcm;
    private BranchEntity branch2Hanoi;
    private UserEntity staffBranch1;
    private UserEntity staffBranch2;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        branchSecurityGuard = new BranchSecurityGuard();
        verificationDocumentPolicy = new VerificationDocumentPolicy();

        contractService = new ContractServiceImpl(
                contractRepository,
                customerRepository,
                collateralRepository,
                branchRepository,
                userRepository,
                cashbookService,
                ltvPolicyService,
                verificationDocumentPolicy,
                interestPaymentRepository,
                branchSecurityGuard,
                provinceRepository,
                districtRepository,
                wardRepository
        );

        branch1Hcm = BranchEntity.builder()
                .branchCode("CN-HCM-01")
                .branchName("Chi nhánh Quận 1")
                .provinceCode("79")
                .districtCode("760")
                .wardCode("26740")
                .addressDetail("100 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP.HCM")
                .currentCashBalance(new BigDecimal("500000000.00"))
                .active(true)
                .build();
        branch1Hcm.setId(1L);

        branch2Hanoi = BranchEntity.builder()
                .branchCode("CN-HN-01")
                .branchName("Chi nhánh Cầu Giấy")
                .provinceCode("01")
                .districtCode("005")
                .wardCode("00167")
                .addressDetail("200 Cầu Giấy, Phường Dịch Vọng Hậu, Quận Cầu Giấy, Hà Nội")
                .currentCashBalance(new BigDecimal("400000000.00"))
                .active(true)
                .build();
        branch2Hanoi.setId(2L);

        staffBranch1 = UserEntity.builder()
                .username("staff_hcm_q1")
                .fullName("Nhân viên Quận 1")
                .role(RoleEnum.STAFF)
                .branch(branch1Hcm)
                .active(true)
                .build();
        staffBranch1.setId(11L);

        staffBranch2 = UserEntity.builder()
                .username("staff_hn_cg")
                .fullName("Nhân viên Cầu Giấy")
                .role(RoleEnum.STAFF)
                .branch(branch2Hanoi)
                .active(true)
                .build();
        staffBranch2.setId(22L);

        lenient().when(branchRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(branch1Hcm));
        lenient().when(branchRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(branch2Hanoi));
        lenient().when(branchRepository.findById(1L)).thenReturn(Optional.of(branch1Hcm));
        lenient().when(branchRepository.findById(2L)).thenReturn(Optional.of(branch2Hanoi));
        lenient().when(userRepository.findByIdAndDeletedFalse(11L)).thenReturn(Optional.of(staffBranch1));
        lenient().when(userRepository.findByIdAndDeletedFalse(22L)).thenReturn(Optional.of(staffBranch2));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UserEntity user) {
        UserPrincipal principal = UserPrincipal.from(user);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Set<String> fieldNames(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("1. CustomerEntity & Customer DTOs have zero branch fields, and originateFullContract never inherits branch province/district/ward/address")
    void customerModelHasNoBranchFields_AndOriginationNeverCopiesBranchLocation() {
        assertThat(fieldNames(CustomerEntity.class)).doesNotContain("branch", "branchId");
        assertThat(fieldNames(CustomerCreateRequest.class)).doesNotContain("branchId");
        assertThat(fieldNames(CustomerUpdateRequest.class)).doesNotContain("branchId");
        assertThat(fieldNames(CustomerResponse.class)).doesNotContain("branchId", "branchCode", "branchName");

        authenticateAs(staffBranch1);
        when(customerRepository.findByIdentityNumberAndDeletedFalse("048195009988")).thenReturn(Optional.empty());
        when(customerRepository.save(any(CustomerEntity.class))).thenAnswer(inv -> {
            CustomerEntity c = inv.getArgument(0);
            c.setId(701L);
            when(customerRepository.findByIdAndDeletedFalse(701L)).thenReturn(Optional.of(c));
            return c;
        });
        when(collateralRepository.save(any(CollateralEntity.class))).thenAnswer(inv -> {
            CollateralEntity col = inv.getArgument(0);
            col.setId(801L);
            when(collateralRepository.findByIdAndDeletedFalse(801L)).thenReturn(Optional.of(col));
            return col;
        });
        when(contractRepository.save(any(ContractEntity.class))).thenAnswer(inv -> {
            ContractEntity ct = inv.getArgument(0);
            ct.setId(901L);
            return ct;
        });

        // Request omits provinceCode, districtCode, wardCode, permanentAddress (only temporary contact address provided)
        QuickContractOriginationRequest req = QuickContractOriginationRequest.builder()
                .branchId(1L) // Branch 1 has 79 / 760 / 26740 / "100 Nguyễn Huệ..."
                .customerFullName("Đặng Quốc Bảo")
                .identityNumber("048195009988")
                .phoneNumber("0905112233")
                .address("Khách sạn Rex, tạm trú ngắn hạn")
                .occupationGroup(CustomerOccupationGroup.FREELANCER)
                .verificationDocType(VerificationDocumentType.RESIDENCE_CONFIRMATION_CT07)
                .verificationDocUrls("https://storage.pawnmaster.vn/docs/ct07_bao.jpg")
                .collateralCategory(CollateralCategory.LAPTOP)
                .collateralName("MacBook Pro 14 M3")
                .serialOrFrameNumber("C02XYZ998877")
                .appraisedValue(new BigDecimal("35000000"))
                .principalAmount(new BigDecimal("15000000"))
                .interestType(InterestType.DAILY_PER_MILLION)
                .interestRate(new BigDecimal("2000"))
                .build();

        contractService.originateFullContract(req, 11L);

        ArgumentCaptor<CustomerEntity> custCaptor = ArgumentCaptor.forClass(CustomerEntity.class);
        verify(customerRepository).save(custCaptor.capture());
        CustomerEntity created = custCaptor.getValue();

        // Must NOT inherit Branch 1's 79 / 760 / 26740 or Branch 1's addressDetail!
        assertThat(created.getProvinceCode()).isNull();
        assertThat(created.getDistrictCode()).isNull();
        assertThat(created.getWardCode()).isNull();
        assertThat(created.getPermanentAddress()).doesNotContain("100 Nguyễn Huệ");
    }

    @Test
    @DisplayName("2. Single global customer (same CCCD) originates contracts at Branch 1 (TP.HCM) and Branch 2 (Hà Nội) without conflict")
    void singleGlobalCustomer_CanOriginateContractsAcrossMultipleBranches() {
        CustomerEntity globalCustomer = CustomerEntity.builder()
                .customerCode("KH-GLOBAL-01")
                .fullName("Vũ Minh Khôi")
                .identityNumber("079192007766")
                .phoneNumber("0912334455")
                .provinceCode("79")
                .districtCode("760")
                .wardCode("26740")
                .permanentAddress("88 Nguyễn Huệ, Phường Bến Nghé, Quận 1, Thành phố Hồ Chí Minh")
                .address("88 Nguyễn Huệ, Phường Bến Nghé, Quận 1, Thành phố Hồ Chí Minh")
                .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                .verificationDocType(VerificationDocumentType.PAYSLIP_BANK_STATEMENT)
                .verificationDocUrls("https://storage.pawnmaster.vn/docs/payslip_khoi.jpg")
                .status(CustomerStatus.ACTIVE)
                .build();
        globalCustomer.setId(555L);

        when(customerRepository.findByIdentityNumberAndDeletedFalse("079192007766"))
                .thenReturn(Optional.of(globalCustomer));
        when(customerRepository.findByIdAndDeletedFalse(555L))
                .thenReturn(Optional.of(globalCustomer));
        when(customerRepository.save(any(CustomerEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(collateralRepository.save(any(CollateralEntity.class))).thenAnswer(inv -> {
            CollateralEntity col = inv.getArgument(0);
            Long assignedId = col.getBranch().getId() == 1L ? 811L : 822L;
            col.setId(assignedId);
            when(collateralRepository.findByIdAndDeletedFalse(assignedId)).thenReturn(Optional.of(col));
            return col;
        });
        when(contractRepository.save(any(ContractEntity.class))).thenAnswer(inv -> {
            ContractEntity ct = inv.getArgument(0);
            ct.setId(ct.getBranch().getId() == 1L ? 911L : 922L);
            return ct;
        });

        // Contract #1 originated at Branch 1 (TP.HCM) by Staff 1
        authenticateAs(staffBranch1);
        ContractResponse c1 = contractService.originateFullContract(QuickContractOriginationRequest.builder()
                .branchId(1L)
                .customerFullName("Vũ Minh Khôi")
                .identityNumber("079192007766")
                .phoneNumber("0912334455")
                .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                .collateralCategory(CollateralCategory.WATCH)
                .collateralName("Rolex Datejust 41")
                .appraisedValue(new BigDecimal("200000000"))
                .principalAmount(new BigDecimal("120000000"))
                .interestType(InterestType.DAILY_PER_MILLION)
                .interestRate(new BigDecimal("2000"))
                .build(), 11L);

        // Contract #2 originated at Branch 2 (Hà Nội) by Staff 2 while customer travels to Hà Nội
        authenticateAs(staffBranch2);
        ContractResponse c2 = contractService.originateFullContract(QuickContractOriginationRequest.builder()
                .branchId(2L)
                .customerFullName("Vũ Minh Khôi")
                .identityNumber("079192007766")
                .phoneNumber("0912334455")
                .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                .collateralCategory(CollateralCategory.PHONE)
                .collateralName("iPhone 16 Pro 512GB")
                .appraisedValue(new BigDecimal("30000000"))
                .principalAmount(new BigDecimal("20000000"))
                .interestType(InterestType.DAILY_PER_MILLION)
                .interestRate(new BigDecimal("2000"))
                .build(), 22L);

        assertThat(c1.getCustomerId()).isEqualTo(555L);
        assertThat(c2.getCustomerId()).isEqualTo(555L);
        assertThat(c1.getBranchId()).isEqualTo(1L);
        assertThat(c2.getBranchId()).isEqualTo(2L);
        assertThat(globalCustomer.getProvinceCode()).isEqualTo("79");
        assertThat(globalCustomer.getWardCode()).isEqualTo("26740");
    }

    @Test
    @DisplayName("3. Cross-branch periodic interest servicing: Branch 2 staff calculates & collects interest for Branch 1 contract, booking cashbook to Branch 2")
    void collectInterest_CrossBranchByBranch2Staff_SucceedsAndBooksCashToBranch2() {
        CustomerEntity customer = CustomerEntity.builder()
                .customerCode("KH-0088")
                .fullName("Lê Hoàng Nam")
                .identityNumber("079204888999")
                .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                .build();
        customer.setId(88L);

        CollateralEntity collateralAtBranch1 = CollateralEntity.builder()
                .collateralCode("TS-HCM-01")
                .customer(customer)
                .branch(branch1Hcm)
                .name("Honda SH 160i")
                .category(CollateralCategory.MOTORBIKE)
                .appraisedValue(new BigDecimal("80000000.00"))
                .status(CollateralStatus.IN_VAULT)
                .build();
        collateralAtBranch1.setId(188L);

        ContractEntity contractAtBranch1 = ContractEntity.builder()
                .contractCode("HD-2611-B01-0001")
                .branch(branch1Hcm)
                .customer(customer)
                .collateral(collateralAtBranch1)
                .principalAmount(new BigDecimal("50000000.00"))
                .remainingPrincipal(new BigDecimal("50000000.00"))
                .interestType(InterestType.DAILY_PER_MILLION)
                .interestRate(new BigDecimal("2000"))
                .storageFeeRate(new BigDecimal("500"))
                .loanTermDays(30)
                .paymentCycleDays(10)
                .startDate(LocalDate.of(2026, 11, 1))
                .maturityDate(LocalDate.of(2026, 12, 1))
                .nextInterestDueDate(LocalDate.of(2026, 11, 11))
                .totalInterestPaid(BigDecimal.ZERO)
                .status(ContractStatus.ACTIVE)
                .build();
        contractAtBranch1.setId(601L);

        when(contractRepository.findByIdAndDeletedFalse(601L)).thenReturn(Optional.of(contractAtBranch1));
        when(interestPaymentRepository.save(any(InterestPaymentEntity.class))).thenAnswer(inv -> {
            InterestPaymentEntity p = inv.getArgument(0);
            p.setId(999L);
            return p;
        });

        // Authenticate as Staff at Branch 2 (Hà Nội, branchId=2)
        authenticateAs(staffBranch2);

        InterestCalculationResponse calc = contractService.calculateContractInterest(
                601L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 11));
        assertThat(calc.getDaysCount()).isEqualTo(10);
        assertThat(calc.getInterestAmount()).isEqualByComparingTo("1000000.00");
        assertThat(calc.getStorageFeeAmount()).isEqualByComparingTo("250000.00");
        assertThat(calc.getTotalDue()).isEqualByComparingTo("1250000.00");

        InterestCollectionRequest collectReq = InterestCollectionRequest.builder()
                .interestAmount(new BigDecimal("1000000.00"))
                .storageFeeAmount(new BigDecimal("250000.00"))
                .paymentMethod("CASH")
                .notes("Khách đóng lãi kỳ 1 tại quầy Chi nhánh Cầu Giấy")
                .build();

        InterestPaymentResponse paymentRes = contractService.collectInterest(601L, collectReq, 22L);

        assertThat(paymentRes.getCollectionBranchId()).isEqualTo(2L);
        assertThat(paymentRes.getCollectionBranchCode()).isEqualTo("CN-HN-01");
        assertThat(paymentRes.getCollectionBranchName()).isEqualTo("Chi nhánh Cầu Giấy");
        assertThat(paymentRes.getTotalAmount()).isEqualByComparingTo("1250000.00");

        // Cash-in MUST go to collecting counter Branch 2 (2L), NOT Contract's Branch 1!
        verify(cashbookService).collectCashIn(
                eq(2L),
                eq(new BigDecimal("1250000.00")),
                eq(TransactionCategory.INTEREST_COLLECTION),
                eq(601L),
                eq(22L),
                anyString()
        );
    }

    @Test
    @DisplayName("4. Strict redemption gate: Branch 2 staff or mismatched ContractRedeemRequest.branchId=2L is blocked with BRANCH_ACCESS_DENIED when collateral is in Branch 1")
    void redeemContract_CrossBranchAttempt_ThrowsBranchAccessDeniedAndLeavesCollateralInVault() {
        CollateralEntity collateralAtBranch1 = CollateralEntity.builder()
                .collateralCode("TS-HCM-02")
                .branch(branch1Hcm)
                .name("Vàng miếng SJC 5 chỉ")
                .category(CollateralCategory.GOLD_JEWELRY)
                .appraisedValue(new BigDecimal("40000000.00"))
                .status(CollateralStatus.IN_VAULT)
                .build();
        collateralAtBranch1.setId(189L);

        ContractEntity contractAtBranch1 = ContractEntity.builder()
                .contractCode("HD-2611-B01-0002")
                .branch(branch1Hcm)
                .collateral(collateralAtBranch1)
                .principalAmount(new BigDecimal("30000000.00"))
                .remainingPrincipal(new BigDecimal("30000000.00"))
                .status(ContractStatus.ACTIVE)
                .build();
        contractAtBranch1.setId(602L);

        when(contractRepository.findByIdAndDeletedFalse(602L)).thenReturn(Optional.of(contractAtBranch1));

        // Case A: Staff at Branch 2 (branchId=2) attempts redemption
        authenticateAs(staffBranch2);
        assertThatThrownBy(() -> contractService.redeemContract(602L, 22L, "Khách đòi chuộc đồ tại Hà Nội"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.BRANCH_ACCESS_DENIED))
                .hasMessageContaining("CN-HCM-01");

        // Case B: ContractRedeemRequest explicitly passes mismatched branchId = 2L
        ContractRedeemRequest mismatchReq = ContractRedeemRequest.builder()
                .branchId(2L)
                .notes("Chuộc khác chi nhánh")
                .build();
        assertThatThrownBy(() -> contractService.redeemContract(602L, mismatchReq, 11L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.BRANCH_ACCESS_DENIED))
                .hasMessageContaining("CN-HCM-01");

        // Collateral MUST remain IN_VAULT, Contract MUST remain ACTIVE, and zero cashbook entries!
        assertThat(collateralAtBranch1.getStatus()).isEqualTo(CollateralStatus.IN_VAULT);
        assertThat(contractAtBranch1.getStatus()).isEqualTo(ContractStatus.ACTIVE);
        verify(cashbookService, never()).collectCashIn(any(), any(), any(TransactionCategory.class), any(), any(), any());
    }

    @Test
    @DisplayName("5. Strict redemption gate: Staff at Branch 1 (holding physical collateral) redeems contract successfully")
    void redeemContract_AtCollateralHoldingBranch1_SucceedsAndReleasesCollateral() {
        CollateralEntity collateralAtBranch1 = CollateralEntity.builder()
                .collateralCode("TS-HCM-03")
                .branch(branch1Hcm)
                .name("Vàng miếng SJC 5 chỉ")
                .category(CollateralCategory.GOLD_JEWELRY)
                .appraisedValue(new BigDecimal("40000000.00"))
                .status(CollateralStatus.IN_VAULT)
                .build();
        collateralAtBranch1.setId(190L);

        ContractEntity contractAtBranch1 = ContractEntity.builder()
                .contractCode("HD-2611-B01-0003")
                .branch(branch1Hcm)
                .collateral(collateralAtBranch1)
                .principalAmount(new BigDecimal("30000000.00"))
                .remainingPrincipal(new BigDecimal("30000000.00"))
                .status(ContractStatus.ACTIVE)
                .build();
        contractAtBranch1.setId(603L);

        when(contractRepository.findByIdAndDeletedFalse(603L)).thenReturn(Optional.of(contractAtBranch1));

        authenticateAs(staffBranch1);
        ContractRedeemRequest validReq = ContractRedeemRequest.builder()
                .branchId(1L)
                .notes("Khách chuộc trực tiếp tại kho Quận 1")
                .build();

        ContractResponse redeemed = contractService.redeemContract(603L, validReq, 11L);

        assertThat(redeemed.getStatus()).isEqualTo(ContractStatus.REDEEMED);
        assertThat(contractAtBranch1.getRemainingPrincipal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(collateralAtBranch1.getStatus()).isEqualTo(CollateralStatus.REDEEMED);

        verify(cashbookService).collectCashIn(
                eq(1L),
                eq(new BigDecimal("30000000.00")),
                eq(TransactionCategory.PRINCIPAL_COLLECTION),
                eq(603L),
                eq(11L),
                eq("Khách chuộc trực tiếp tại kho Quận 1")
        );
    }
}

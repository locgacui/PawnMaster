package com.pawnmaster.api.modules.customer;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.branch.BranchRepository;
import com.pawnmaster.api.modules.branch.DistrictEntity;
import com.pawnmaster.api.modules.branch.DistrictRepository;
import com.pawnmaster.api.modules.branch.ProvinceEntity;
import com.pawnmaster.api.modules.branch.ProvinceRepository;
import com.pawnmaster.api.modules.branch.WardEntity;
import com.pawnmaster.api.modules.branch.WardRepository;
import com.pawnmaster.api.modules.cashbook.CashbookService;
import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.collateral.CollateralEntity;
import com.pawnmaster.api.modules.collateral.CollateralRepository;
import com.pawnmaster.api.modules.collateral.LtvPolicyService;
import com.pawnmaster.api.modules.contract.ContractEntity;
import com.pawnmaster.api.modules.contract.ContractRepository;
import com.pawnmaster.api.modules.contract.ContractServiceImpl;
import com.pawnmaster.api.modules.contract.InterestPaymentRepository;
import com.pawnmaster.api.modules.contract.InterestType;
import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import com.pawnmaster.api.modules.contract.dto.QuickContractOriginationRequest;
import com.pawnmaster.api.modules.customer.dto.CustomerCreateRequest;
import com.pawnmaster.api.modules.customer.dto.CustomerResponse;
import com.pawnmaster.api.modules.customer.dto.CustomerUpdateRequest;
import com.pawnmaster.api.modules.user.UserRepository;
import com.pawnmaster.api.security.BranchSecurityGuard;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Customer & Contract 3-Tier Cascading Address (Province -> District -> Ward) Tests")
class CustomerAddressCascadeTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private ProvinceRepository provinceRepository;

    @Mock
    private DistrictRepository districtRepository;

    @Mock
    private WardRepository wardRepository;

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private CollateralRepository collateralRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CashbookService cashbookService;

    @Mock
    private LtvPolicyService ltvPolicyService;

    @Mock
    private InterestPaymentRepository interestPaymentRepository;

    @Mock
    private BranchSecurityGuard branchSecurityGuard;

    private VerificationDocumentPolicy verificationDocumentPolicy;
    private CustomerServiceImpl customerService;
    private ContractServiceImpl contractService;

    private ProvinceEntity hcmProvince;
    private ProvinceEntity hnProvince;
    private DistrictEntity district1Hcm;
    private DistrictEntity district3Hcm;
    private DistrictEntity cauGiayHn;
    private WardEntity benNgheWard;
    private WardEntity voThiSauWard;
    private WardEntity dichVongHauWard;
    private BranchEntity hcmBranch1;

    @BeforeEach
    void setUp() {
        verificationDocumentPolicy = new VerificationDocumentPolicy();

        customerService = new CustomerServiceImpl(
                customerRepository,
                branchRepository,
                verificationDocumentPolicy,
                provinceRepository,
                districtRepository,
                wardRepository
        );

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

        hcmProvince = ProvinceEntity.builder()
                .code("79")
                .name("Hồ Chí Minh")
                .fullName("Thành phố Hồ Chí Minh")
                .build();

        hnProvince = ProvinceEntity.builder()
                .code("01")
                .name("Hà Nội")
                .fullName("Thành phố Hà Nội")
                .build();

        district1Hcm = DistrictEntity.builder()
                .code("760")
                .name("1")
                .fullName("Quận 1")
                .provinceCode("79")
                .build();

        district3Hcm = DistrictEntity.builder()
                .code("770")
                .name("3")
                .fullName("Quận 3")
                .provinceCode("79")
                .build();

        cauGiayHn = DistrictEntity.builder()
                .code("005")
                .name("Cầu Giấy")
                .fullName("Quận Cầu Giấy")
                .provinceCode("01")
                .build();

        benNgheWard = WardEntity.builder()
                .code("26740")
                .name("Bến Nghé")
                .fullName("Phường Bến Nghé")
                .districtCode("760")
                .build();

        voThiSauWard = WardEntity.builder()
                .code("27139")
                .name("Võ Thị Sáu")
                .fullName("Phường Võ Thị Sáu")
                .districtCode("770")
                .build();

        dichVongHauWard = WardEntity.builder()
                .code("00167")
                .name("Dịch Vọng Hậu")
                .fullName("Phường Dịch Vọng Hậu")
                .districtCode("005")
                .build();

        hcmBranch1 = BranchEntity.builder()
                .branchCode("CN-HCM-01")
                .branchName("Chi nhánh Quận 1")
                .provinceCode("79")
                .districtCode("760")
                .wardCode("26740")
                .addressDetail("100 Nguyễn Huệ, Phường Bến Nghé, Quận 1, Thành phố Hồ Chí Minh")
                .currentCashBalance(new BigDecimal("500000000.00"))
                .build();
        hcmBranch1.setId(1L);

        lenient().when(provinceRepository.count()).thenReturn(63L);
        lenient().when(districtRepository.count()).thenReturn(700L);
        lenient().when(wardRepository.count()).thenReturn(10000L);

        lenient().when(provinceRepository.findById("79")).thenReturn(Optional.of(hcmProvince));
        lenient().when(provinceRepository.findById("01")).thenReturn(Optional.of(hnProvince));

        lenient().when(districtRepository.findById("760")).thenReturn(Optional.of(district1Hcm));
        lenient().when(districtRepository.findById("770")).thenReturn(Optional.of(district3Hcm));
        lenient().when(districtRepository.findById("005")).thenReturn(Optional.of(cauGiayHn));

        lenient().when(wardRepository.findById("26740")).thenReturn(Optional.of(benNgheWard));
        lenient().when(wardRepository.findById("27139")).thenReturn(Optional.of(voThiSauWard));
        lenient().when(wardRepository.findById("00167")).thenReturn(Optional.of(dichVongHauWard));
    }

    @Test
    @DisplayName("1. createCustomer persists provinceCode, districtCode, wardCode and joins fullName into permanentAddress & fullAddress")
    void createCustomer_PersistsCodesAndJoinsFullAddress() {
        when(customerRepository.existsByIdentityNumber("079201004821")).thenReturn(false);
        when(customerRepository.save(any(CustomerEntity.class))).thenAnswer(inv -> {
            CustomerEntity c = inv.getArgument(0);
            c.setId(55L);
            return c;
        });

        CustomerCreateRequest req = CustomerCreateRequest.builder()
                .fullName("Nguyễn Văn An")
                .identityNumber("079201004821")
                .phoneNumber("0901234567")
                .provinceCode("79")
                .districtCode("760")
                .wardCode("26740")
                .addressDetail("88 Nguyễn Huệ")
                .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                .monthlyIncome(new BigDecimal("18000000"))
                .hasSocialInsurance(true)
                .verificationDocType(VerificationDocumentType.LABOR_CONTRACT)
                .verificationDocUrls(List.of("https://storage.pawnmaster.vn/docs/kh_002_contract.jpg"))
                .build();

        CustomerResponse res = customerService.createCustomer(req);

        ArgumentCaptor<CustomerEntity> captor = ArgumentCaptor.forClass(CustomerEntity.class);
        verify(customerRepository).save(captor.capture());
        CustomerEntity saved = captor.getValue();

        String expectedFull = "88 Nguyễn Huệ, Phường Bến Nghé, Quận 1, Thành phố Hồ Chí Minh";
        assertThat(saved.getProvinceCode()).isEqualTo("79");
        assertThat(saved.getDistrictCode()).isEqualTo("760");
        assertThat(saved.getWardCode()).isEqualTo("26740");
        assertThat(saved.getPermanentAddress()).isEqualTo(expectedFull);
        assertThat(saved.getAddress()).isEqualTo(expectedFull);

        assertThat(res.getProvinceCode()).isEqualTo("79");
        assertThat(res.getProvinceName()).isEqualTo("Thành phố Hồ Chí Minh");
        assertThat(res.getDistrictCode()).isEqualTo("760");
        assertThat(res.getDistrictName()).isEqualTo("Quận 1");
        assertThat(res.getWardCode()).isEqualTo("26740");
        assertThat(res.getWardName()).isEqualTo("Phường Bến Nghé");
        assertThat(res.getAddressDetail()).isEqualTo("88 Nguyễn Huệ");
        assertThat(res.getPermanentAddress()).isEqualTo(expectedFull);
        assertThat(res.getFullAddress()).isEqualTo(expectedFull);
    }

    @Test
    @DisplayName("2. createCustomer deduplicates trailing administrative suffixes and preserves distinct contact address")
    void createCustomer_AvoidsDuplicateSuffixesAndPreservesTemporaryContactAddress() {
        when(customerRepository.existsByIdentityNumber("079198005432")).thenReturn(false);
        when(customerRepository.save(any(CustomerEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerCreateRequest req = CustomerCreateRequest.builder()
                .fullName("Trần Thị Mai")
                .identityNumber("079198005432")
                .phoneNumber("0913456789")
                .provinceCode("79")
                .districtCode("770")
                .wardCode("27139")
                .addressDetail("154 Nguyễn Đình Chiểu, Phường Võ Thị Sáu, Quận 3, Thành phố Hồ Chí Minh")
                .address("Chung cư Vinhomes Central Park, Bình Thạnh")
                .occupationGroup(CustomerOccupationGroup.SMALL_MERCHANT)
                .monthlyIncome(new BigDecimal("32000000"))
                .verificationDocType(VerificationDocumentType.BUSINESS_LICENSE_OR_STALL_LEASE)
                .verificationDocUrls(List.of("https://storage.pawnmaster.vn/docs/kh_003_stall.jpg"))
                .build();

        CustomerResponse res = customerService.createCustomer(req);

        assertThat(res.getAddressDetail()).isEqualTo("154 Nguyễn Đình Chiểu");
        assertThat(res.getPermanentAddress())
                .isEqualTo("154 Nguyễn Đình Chiểu, Phường Võ Thị Sáu, Quận 3, Thành phố Hồ Chí Minh");
        assertThat(res.getAddress()).isEqualTo("Chung cư Vinhomes Central Park, Bình Thạnh");
    }

    @Test
    @DisplayName("3. updateCustomer updates wardCode & districtCode, preserves street detail, and updates KYC fields")
    void updateCustomer_UpdatesCodesAndRejoinsExistingStreetDetail() {
        CustomerEntity existing = CustomerEntity.builder()
                .customerCode("KH-0099")
                .fullName("Nguyễn Văn An")
                .identityNumber("079201004821")
                .phoneNumber("0901234567")
                .provinceCode("79")
                .districtCode("760")
                .wardCode("26740")
                .address("88 Nguyễn Huệ, Phường Bến Nghé, Quận 1, Thành phố Hồ Chí Minh")
                .permanentAddress("88 Nguyễn Huệ, Phường Bến Nghé, Quận 1, Thành phố Hồ Chí Minh")
                .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                .status(CustomerStatus.ACTIVE)
                .build();
        existing.setId(99L);

        when(customerRepository.findById(99L)).thenReturn(Optional.of(existing));
        when(customerRepository.save(any(CustomerEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerUpdateRequest updateReq = CustomerUpdateRequest.builder()
                .districtCode("770")
                .wardCode("27139")
                .identityIssueDate(LocalDate.of(2022, 6, 15))
                .identityIssuePlace("Cục Cảnh sát QLHC về TTXH")
                .gender("NAM")
                .idCardFrontUrl("https://storage.pawnmaster.vn/kyc/front.jpg")
                .build();

        CustomerResponse updated = customerService.updateCustomer(99L, updateReq);

        assertThat(updated.getProvinceCode()).isEqualTo("79");
        assertThat(updated.getDistrictCode()).isEqualTo("770");
        assertThat(updated.getWardCode()).isEqualTo("27139");
        assertThat(updated.getPermanentAddress())
                .isEqualTo("88 Nguyễn Huệ, Phường Võ Thị Sáu, Quận 3, Thành phố Hồ Chí Minh");
        assertThat(updated.getIdentityIssueDate()).isEqualTo(LocalDate.of(2022, 6, 15));
        assertThat(updated.getIdCardFrontUrl()).isEqualTo("https://storage.pawnmaster.vn/kyc/front.jpg");
    }

    @Test
    @DisplayName("4. Parent-child hierarchy validation rejects mismatched (provinceCode, districtCode) and (districtCode, wardCode)")
    void createCustomer_MismatchedAdminHierarchy_ThrowsBusinessException() {
        when(customerRepository.existsByIdentityNumber("079999888777")).thenReturn(false);

        // Case A: District 005 (Cầu Giấy - Hà Nội) paired with Province 79 (TP.HCM)
        CustomerCreateRequest badDistrictReq = CustomerCreateRequest.builder()
                .fullName("Lê Văn Sai Quận")
                .identityNumber("079999888777")
                .phoneNumber("0909888777")
                .provinceCode("79")
                .districtCode("005")
                .wardCode("00167")
                .addressDetail("10 Đường Test")
                .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                .verificationDocType(VerificationDocumentType.LABOR_CONTRACT)
                .verificationDocUrls(List.of("https://storage.pawnmaster.vn/docs/doc.jpg"))
                .build();

        assertThatThrownBy(() -> customerService.createCustomer(badDistrictReq))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessageContaining("Quận/Huyện [005] không thuộc Tỉnh/Thành phố [79]");

        // Case B: Ward 00167 (Dịch Vọng Hậu - Cầu Giấy) paired with District 760 (Quận 1 - TP.HCM)
        CustomerCreateRequest badWardReq = CustomerCreateRequest.builder()
                .fullName("Lê Văn Sai Phường")
                .identityNumber("079999888777")
                .phoneNumber("0909888777")
                .provinceCode("79")
                .districtCode("760")
                .wardCode("00167")
                .addressDetail("10 Đường Test")
                .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                .verificationDocType(VerificationDocumentType.LABOR_CONTRACT)
                .verificationDocUrls(List.of("https://storage.pawnmaster.vn/docs/doc.jpg"))
                .build();

        assertThatThrownBy(() -> customerService.createCustomer(badWardReq))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessageContaining("Phường/Xã [00167] không thuộc Quận/Huyện [760]");

        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("5. originateFullContract persists new customer's own (provinceCode, districtCode, wardCode) & composed permanentAddress instead of overwriting with branch codes")
    void originateFullContract_NewCustomerInHanoiAtHcmBranch_PersistsCustomerCascadeCodes() {
        when(branchRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(hcmBranch1));
        when(customerRepository.findByIdentityNumberAndDeletedFalse("001189002341")).thenReturn(Optional.empty());
        when(customerRepository.save(any(CustomerEntity.class))).thenAnswer(inv -> {
            CustomerEntity c = inv.getArgument(0);
            c.setId(301L);
            when(customerRepository.findByIdAndDeletedFalse(301L)).thenReturn(Optional.of(c));
            return c;
        });
        when(collateralRepository.save(any(CollateralEntity.class))).thenAnswer(inv -> {
            CollateralEntity col = inv.getArgument(0);
            col.setId(401L);
            when(collateralRepository.findByIdAndDeletedFalse(401L)).thenReturn(Optional.of(col));
            return col;
        });
        when(contractRepository.save(any(ContractEntity.class))).thenAnswer(inv -> {
            ContractEntity ct = inv.getArgument(0);
            ct.setId(501L);
            return ct;
        });

        QuickContractOriginationRequest req = QuickContractOriginationRequest.builder()
                .branchId(1L) // Branch 1 is in TP.HCM (79 / 760 / 26740)
                .customerFullName("Phạm Hoàng Long")
                .identityNumber("001189002341")
                .phoneNumber("0988777666")
                .provinceCode("01")    // Customer's permanent residence is in Hà Nội!
                .districtCode("005")
                .wardCode("00167")
                .addressDetail("25 Trần Thái Tông")
                .address("90 Nguyễn Hữu Cảnh, Bình Thạnh, TP.HCM")
                .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                .monthlyIncome(new BigDecimal("25000000"))
                .verificationDocType(VerificationDocumentType.PAYSLIP_BANK_STATEMENT)
                .verificationDocUrls("https://storage.pawnmaster.vn/docs/payslip_long.jpg")
                .collateralCategory(CollateralCategory.PHONE)
                .collateralName("iPhone 16 Pro Max 256GB")
                .serialOrFrameNumber("356998012345678")
                .appraisedValue(new BigDecimal("30000000"))
                .principalAmount(new BigDecimal("20000000"))
                .interestType(InterestType.DAILY_PER_MILLION)
                .interestRate(new BigDecimal("2000"))
                .loanTermDays(30)
                .paymentCycleDays(10)
                .startDate(LocalDate.of(2026, 11, 11))
                .build();

        ContractResponse contractRes = contractService.originateFullContract(req, 10L);

        ArgumentCaptor<CustomerEntity> custCaptor = ArgumentCaptor.forClass(CustomerEntity.class);
        verify(customerRepository).save(custCaptor.capture());
        CustomerEntity savedCust = custCaptor.getValue();

        String expectedPerm = "25 Trần Thái Tông, Phường Dịch Vọng Hậu, Quận Cầu Giấy, Thành phố Hà Nội";
        assertThat(savedCust.getProvinceCode()).isEqualTo("01");
        assertThat(savedCust.getDistrictCode()).isEqualTo("005");
        assertThat(savedCust.getWardCode()).isEqualTo("00167");
        assertThat(savedCust.getPermanentAddress()).isEqualTo(expectedPerm);
        assertThat(savedCust.getAddress()).isEqualTo("90 Nguyễn Hữu Cảnh, Bình Thạnh, TP.HCM");

        assertThat(contractRes.getCustomerProvinceCode()).isEqualTo("01");
        assertThat(contractRes.getCustomerDistrictCode()).isEqualTo("005");
        assertThat(contractRes.getCustomerWardCode()).isEqualTo("00167");
        assertThat(contractRes.getCustomerPermanentAddress()).isEqualTo(expectedPerm);
    }

    @Test
    @DisplayName("6. originateFullContract updates existing customer's (provinceCode, districtCode, wardCode) & composed permanentAddress")
    void originateFullContract_ExistingCustomer_UpdatesAdministrativeCodesAndPermanentAddress() {
        CustomerEntity existingCust = CustomerEntity.builder()
                .customerCode("KH-2610-0001")
                .fullName("Nguyễn Văn An")
                .identityNumber("079201004821")
                .phoneNumber("0901234567")
                .provinceCode("79")
                .districtCode("760")
                .status(CustomerStatus.ACTIVE)
                .build();
        existingCust.setId(88L);

        when(branchRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(hcmBranch1));
        when(customerRepository.findByIdentityNumberAndDeletedFalse("079201004821")).thenReturn(Optional.of(existingCust));
        when(customerRepository.findByIdAndDeletedFalse(88L)).thenReturn(Optional.of(existingCust));
        when(customerRepository.save(any(CustomerEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(collateralRepository.save(any(CollateralEntity.class))).thenAnswer(inv -> {
            CollateralEntity col = inv.getArgument(0);
            col.setId(402L);
            when(collateralRepository.findByIdAndDeletedFalse(402L)).thenReturn(Optional.of(col));
            return col;
        });
        when(contractRepository.save(any(ContractEntity.class))).thenAnswer(inv -> {
            ContractEntity ct = inv.getArgument(0);
            ct.setId(502L);
            return ct;
        });

        QuickContractOriginationRequest req = QuickContractOriginationRequest.builder()
                .branchId(1L)
                .customerFullName("Nguyễn Văn An")
                .identityNumber("079201004821")
                .phoneNumber("0901234567")
                .provinceCode("79")
                .districtCode("760")
                .wardCode("26740")
                .addressDetail("88 Nguyễn Huệ")
                .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                .collateralCategory(CollateralCategory.MOTORBIKE)
                .collateralName("Honda SH 160i ABS")
                .licensePlate("59A3-999.99")
                .appraisedValue(new BigDecimal("80000000"))
                .principalAmount(new BigDecimal("60000000"))
                .interestType(InterestType.DAILY_PER_MILLION)
                .interestRate(new BigDecimal("2000"))
                .build();

        ContractResponse res = contractService.originateFullContract(req, 10L);

        assertThat(existingCust.getWardCode()).isEqualTo("26740");
        assertThat(existingCust.getPermanentAddress())
                .isEqualTo("88 Nguyễn Huệ, Phường Bến Nghé, Quận 1, Thành phố Hồ Chí Minh");
        assertThat(res.getCustomerWardCode()).isEqualTo("26740");
        assertThat(res.getCustomerPermanentAddress())
                .isEqualTo("88 Nguyễn Huệ, Phường Bến Nghé, Quận 1, Thành phố Hồ Chí Minh");
    }
}

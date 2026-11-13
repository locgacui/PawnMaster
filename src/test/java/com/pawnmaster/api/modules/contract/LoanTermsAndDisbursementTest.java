package com.pawnmaster.api.modules.contract;

import com.pawnmaster.api.common.BaseResponse;
import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.bank.BankController;
import com.pawnmaster.api.modules.bank.BankEntity;
import com.pawnmaster.api.modules.bank.BankRepository;
import com.pawnmaster.api.modules.bank.dto.BankResponse;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.branch.BranchRepository;
import com.pawnmaster.api.modules.cashbook.CashbookEntity;
import com.pawnmaster.api.modules.cashbook.CashbookRepository;
import com.pawnmaster.api.modules.cashbook.CashbookService;
import com.pawnmaster.api.modules.cashbook.CashbookServiceImpl;
import com.pawnmaster.api.modules.cashbook.dto.CashbookResponse;
import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.collateral.CollateralEntity;
import com.pawnmaster.api.modules.collateral.CollateralRepository;
import com.pawnmaster.api.modules.collateral.CollateralStatus;
import com.pawnmaster.api.modules.collateral.LtvPolicyService;
import com.pawnmaster.api.modules.contract.dto.ContractCreateRequest;
import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import com.pawnmaster.api.modules.contract.dto.InterestCalculationResponse;
import com.pawnmaster.api.modules.customer.CustomerEntity;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.CustomerRepository;
import com.pawnmaster.api.modules.customer.VerificationDocumentPolicy;
import com.pawnmaster.api.modules.customer.VerificationDocumentType;
import com.pawnmaster.api.modules.user.UserRepository;
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
@DisplayName("Section 3: PERCENT_WEEKLY Interest & Vietnamese Bank Disbursement Tests")
class LoanTermsAndDisbursementTest {

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
    private VerificationDocumentPolicy verificationDocumentPolicy;

    @Mock
    private InterestPaymentRepository interestPaymentRepository;

    @Mock
    private BankRepository bankRepository;

    @Mock
    private CashbookRepository cashbookRepository;

    private ContractServiceImpl contractService;
    private CashbookServiceImpl cashbookServiceImpl;

    private BranchEntity branch;
    private CustomerEntity customer;
    private CollateralEntity collateral;
    private BankEntity vcbBank;

    @BeforeEach
    void setUp() {
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
                null,
                null,
                null,
                null,
                null,
                bankRepository
        );

        cashbookServiceImpl = new CashbookServiceImpl(
                cashbookRepository,
                branchRepository,
                contractRepository,
                customerRepository
        );

        branch = BranchEntity.builder()
                .branchCode("CN-HCM-01")
                .branchName("Chi nhánh Quận 1")
                .provinceCode("79")
                .districtCode("760")
                .currentCashBalance(new BigDecimal("500000000.00"))
                .active(true)
                .build();
        branch.setId(1L);

        customer = CustomerEntity.builder()
                .customerCode("KH-001")
                .fullName("Nguyễn Văn Dũng")
                .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                .verificationDocType(VerificationDocumentType.PAYSLIP_BANK_STATEMENT)
                .verificationDocUrls("/uploads/customers/079201004821/verification-docs")
                .build();
        customer.setId(100L);

        collateral = CollateralEntity.builder()
                .collateralCode("TS-001")
                .customer(customer)
                .branch(branch)
                .name("Honda SH 150i")
                .category(CollateralCategory.MOTORBIKE)
                .appraisedValue(new BigDecimal("50000000.00"))
                .maxLtvPercent(new BigDecimal("0.90"))
                .status(CollateralStatus.IN_VAULT)
                .build();
        collateral.setId(200L);

        vcbBank = BankEntity.builder()
                .bankCode("VCB")
                .bin("970436")
                .shortName("Vietcombank")
                .fullName("Ngân hàng TMCP Ngoại thương Việt Nam")
                .bankGroup("STATE_POPULAR")
                .displayOrder(1)
                .active(true)
                .build();
        vcbBank.setId(1L);
    }

    @Test
    @DisplayName("PERCENT_WEEKLY calculates exact 7-day, 14-day interest & weekly storage fee")
    void percentWeeklyCalculatesInterestAndStorageFeeAccurately() {
        ContractEntity weeklyContract = ContractEntity.builder()
                .contractCode("HD-WK-001")
                .principalAmount(new BigDecimal("20000000.00"))
                .remainingPrincipal(new BigDecimal("20000000.00"))
                .interestType(InterestType.PERCENT_WEEKLY)
                .interestRate(new BigDecimal("1.40"))
                .storageFeeRate(new BigDecimal("0.35"))
                .build();
        weeklyContract.setId(10L);

        LocalDate start = LocalDate.of(2026, 11, 1);

        InterestCalculationResponse sevenDays = contractService.calculateInterest(weeklyContract, start, start.plusDays(7));
        assertEquals(0, new BigDecimal("280000.00").compareTo(sevenDays.getInterestAmount()));
        assertEquals(0, new BigDecimal("70000.00").compareTo(sevenDays.getStorageFeeAmount()));
        assertEquals(0, new BigDecimal("350000.00").compareTo(sevenDays.getTotalDue()));

        InterestCalculationResponse fourteenDays = contractService.calculateInterest(weeklyContract, start, start.plusDays(14));
        assertEquals(0, new BigDecimal("560000.00").compareTo(fourteenDays.getInterestAmount()));
        assertEquals(0, new BigDecimal("140000.00").compareTo(fourteenDays.getStorageFeeAmount()));
    }

    @Test
    @DisplayName("BANK_TRANSFER origination resolves bank name, normalizes account number & auto-derives uppercase ASCII holder")
    void bankTransferCreateContractNormalizesFieldsAndAutoDerivesHolder() {
        when(customerRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(customer));
        when(collateralRepository.findByIdAndDeletedFalse(200L)).thenReturn(Optional.of(collateral));
        when(branchRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(branch));
        when(bankRepository.findByBankCodeIgnoreCaseAndDeletedFalse("VCB")).thenReturn(Optional.of(vcbBank));
        when(contractRepository.save(any(ContractEntity.class))).thenAnswer(inv -> {
            ContractEntity c = inv.getArgument(0);
            c.setId(501L);
            return c;
        });

        ContractCreateRequest req = ContractCreateRequest.builder()
                .customerId(100L)
                .collateralId(200L)
                .branchId(1L)
                .principalAmount(new BigDecimal("20000000.00"))
                .interestType(InterestType.PERCENT_WEEKLY)
                .interestRate(new BigDecimal("1.40"))
                .storageFeeRate(BigDecimal.ZERO)
                .loanTermDays(28)
                .paymentCycleDays(7)
                .startDate(LocalDate.of(2026, 11, 13))
                .disbursementMethod(DisbursementMethod.BANK_TRANSFER)
                .disbursementBankCode("vcb")
                .disbursementAccountNumber(" 0071 0012 34567 ")
                .disbursementAccountHolder("")
                .notes("Khách nhận chuyển khoản nhanh")
                .build();

        ContractResponse res = contractService.createContract(req, null);

        assertEquals(DisbursementMethod.BANK_TRANSFER, res.getDisbursementMethod());
        assertEquals("VCB", res.getDisbursementBankCode());
        assertEquals("Vietcombank", res.getDisbursementBankName());
        assertEquals("0071001234567", res.getDisbursementAccountNumber());
        assertEquals("NGUYEN VAN DUNG", res.getDisbursementAccountHolder());
        assertEquals("Khách nhận chuyển khoản nhanh", res.getNotes());
    }

    @Test
    @DisplayName("CASH origination clears residual bank fields to null")
    void cashDisbursementClearsBankFields() {
        when(customerRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(customer));
        when(collateralRepository.findByIdAndDeletedFalse(200L)).thenReturn(Optional.of(collateral));
        when(branchRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(branch));
        when(contractRepository.save(any(ContractEntity.class))).thenAnswer(inv -> {
            ContractEntity c = inv.getArgument(0);
            c.setId(502L);
            return c;
        });

        ContractCreateRequest req = ContractCreateRequest.builder()
                .customerId(100L)
                .collateralId(200L)
                .branchId(1L)
                .principalAmount(new BigDecimal("20000000.00"))
                .interestType(InterestType.DAILY_PER_MILLION)
                .interestRate(new BigDecimal("2000.00"))
                .storageFeeRate(BigDecimal.ZERO)
                .loanTermDays(30)
                .paymentCycleDays(10)
                .disbursementMethod(DisbursementMethod.CASH)
                .disbursementBankCode("VCB")
                .disbursementBankName("Vietcombank")
                .disbursementAccountNumber("0071001234567")
                .disbursementAccountHolder("NGUYEN VAN DUNG")
                .build();

        ContractResponse res = contractService.createContract(req, null);

        assertEquals(DisbursementMethod.CASH, res.getDisbursementMethod());
        assertNull(res.getDisbursementBankCode());
        assertNull(res.getDisbursementBankName());
        assertNull(res.getDisbursementAccountNumber());
        assertNull(res.getDisbursementAccountHolder());
        verifyNoInteractions(bankRepository);
    }

    @Test
    @DisplayName("BANK_TRANSFER validation rejects missing bank, unknown bank code, inactive bank, and invalid account number")
    void bankTransferValidationRejectsInvalidInputs() {
        when(customerRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(customer));
        when(collateralRepository.findByIdAndDeletedFalse(200L)).thenReturn(Optional.of(collateral));
        when(branchRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(branch));

        ContractCreateRequest missingBankReq = ContractCreateRequest.builder()
                .customerId(100L)
                .collateralId(200L)
                .branchId(1L)
                .principalAmount(new BigDecimal("20000000.00"))
                .interestType(InterestType.DAILY_PER_MILLION)
                .interestRate(new BigDecimal("2000.00"))
                .disbursementMethod(DisbursementMethod.BANK_TRANSFER)
                .disbursementBankCode(" ")
                .disbursementAccountNumber("0071001234567")
                .build();

        BusinessException exMissing = assertThrows(BusinessException.class,
                () -> contractService.createContract(missingBankReq, null));
        assertEquals(ErrorCode.INVALID_REQUEST, exMissing.getErrorCode());

        when(bankRepository.findByBankCodeIgnoreCaseAndDeletedFalse("UNKNOWN")).thenReturn(Optional.empty());
        when(bankRepository.count()).thenReturn(35L);
        ContractCreateRequest unknownBankReq = ContractCreateRequest.builder()
                .customerId(100L)
                .collateralId(200L)
                .branchId(1L)
                .principalAmount(new BigDecimal("20000000.00"))
                .interestType(InterestType.DAILY_PER_MILLION)
                .interestRate(new BigDecimal("2000.00"))
                .disbursementMethod(DisbursementMethod.BANK_TRANSFER)
                .disbursementBankCode("UNKNOWN")
                .disbursementAccountNumber("0071001234567")
                .build();

        assertThrows(BusinessException.class, () -> contractService.createContract(unknownBankReq, null));

        BankEntity inactiveBank = BankEntity.builder()
                .bankCode("OLD")
                .shortName("OldBank")
                .fullName("Ngân hàng cũ")
                .active(false)
                .build();
        when(bankRepository.findByBankCodeIgnoreCaseAndDeletedFalse("OLD")).thenReturn(Optional.of(inactiveBank));
        ContractCreateRequest inactiveBankReq = ContractCreateRequest.builder()
                .customerId(100L)
                .collateralId(200L)
                .branchId(1L)
                .principalAmount(new BigDecimal("20000000.00"))
                .interestType(InterestType.DAILY_PER_MILLION)
                .interestRate(new BigDecimal("2000.00"))
                .disbursementMethod(DisbursementMethod.BANK_TRANSFER)
                .disbursementBankCode("OLD")
                .disbursementAccountNumber("0071001234567")
                .build();

        assertThrows(BusinessException.class, () -> contractService.createContract(inactiveBankReq, null));

        when(bankRepository.findByBankCodeIgnoreCaseAndDeletedFalse("VCB")).thenReturn(Optional.of(vcbBank));
        ContractCreateRequest shortStkReq = ContractCreateRequest.builder()
                .customerId(100L)
                .collateralId(200L)
                .branchId(1L)
                .principalAmount(new BigDecimal("20000000.00"))
                .interestType(InterestType.DAILY_PER_MILLION)
                .interestRate(new BigDecimal("2000.00"))
                .disbursementMethod(DisbursementMethod.BANK_TRANSFER)
                .disbursementBankCode("VCB")
                .disbursementAccountNumber("123")
                .build();

        assertThrows(BusinessException.class, () -> contractService.createContract(shortStkReq, null));
    }

    @Test
    @DisplayName("CashbookServiceImpl.disburseLoan mirrors paymentMethod and bank transfer tag accurately")
    void cashbookDisburseLoanMirrorsBankTransferAndCashMethods() {
        when(branchRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(branch));
        when(cashbookRepository.save(any(CashbookEntity.class))).thenAnswer(inv -> {
            CashbookEntity entity = inv.getArgument(0);
            entity.setId(900L);
            return entity;
        });

        ContractEntity bankContract = ContractEntity.builder()
                .contractCode("HD-CK-001")
                .customer(customer)
                .branch(branch)
                .principalAmount(new BigDecimal("20000000.00"))
                .disbursementMethod(DisbursementMethod.BANK_TRANSFER)
                .disbursementBankCode("VCB")
                .disbursementBankName("Vietcombank")
                .disbursementAccountNumber("0071001234567")
                .disbursementAccountHolder("NGUYEN VAN DUNG")
                .build();
        bankContract.setId(700L);

        when(contractRepository.findByIdAndDeletedFalse(700L)).thenReturn(Optional.of(bankContract));

        CashbookResponse bankOut = cashbookServiceImpl.disburseLoan(
                1L, new BigDecimal("20000000.00"), 700L, 11L, "Giải ngân duyệt hồ sơ");

        assertEquals("BANK_TRANSFER", bankOut.getPaymentMethod());
        assertEquals("Giải ngân duyệt hồ sơ [CK: Vietcombank - STK 0071001234567 - NGUYEN VAN DUNG]",
                bankOut.getDescription());

        ContractEntity cashContract = ContractEntity.builder()
                .contractCode("HD-TM-002")
                .customer(customer)
                .branch(branch)
                .principalAmount(new BigDecimal("10000000.00"))
                .disbursementMethod(DisbursementMethod.CASH)
                .build();
        cashContract.setId(701L);

        when(contractRepository.findByIdAndDeletedFalse(701L)).thenReturn(Optional.of(cashContract));

        ArgumentCaptor<CashbookEntity> savedCaptor = ArgumentCaptor.forClass(CashbookEntity.class);
        CashbookResponse cashOut = cashbookServiceImpl.disburseLoan(
                1L, new BigDecimal("10000000.00"), 701L, 11L, "Giải ngân tại quầy");

        verify(cashbookRepository, times(2)).save(savedCaptor.capture());
        assertEquals("CASH", cashOut.getPaymentMethod());
        assertEquals("Giải ngân tại quầy", savedCaptor.getValue().getDescription());
        assertFalse(cashOut.getDescription().contains("[CK:"));
    }

    @Test
    @DisplayName("BankController returns active banks wrapped in BaseResponse")
    void bankControllerReturnsActiveBanksList() {
        when(bankRepository.findByActiveTrueAndDeletedFalseOrderByDisplayOrderAscShortNameAsc())
                .thenReturn(List.of(vcbBank));

        BankController controller = new BankController(bankRepository);
        BaseResponse<List<BankResponse>> response = controller.getActiveBanks();

        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertEquals(1, response.getData().size());
        assertEquals("VCB", response.getData().get(0).getBankCode());
        assertEquals("Vietcombank", response.getData().get(0).getShortName());
        assertTrue(response.getData().get(0).getDisplayLabel().contains("VCB"));
    }
}

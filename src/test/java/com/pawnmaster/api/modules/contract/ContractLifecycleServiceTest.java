package com.pawnmaster.api.modules.contract;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.cashbook.CashbookService;
import com.pawnmaster.api.modules.cashbook.TransactionCategory;
import com.pawnmaster.api.modules.cashbook.dto.CashbookResponse;
import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.collateral.CollateralEntity;
import com.pawnmaster.api.modules.collateral.CollateralRepository;
import com.pawnmaster.api.modules.collateral.CollateralStatus;
import com.pawnmaster.api.modules.contract.dto.InterestCalculationResponse;
import com.pawnmaster.api.modules.contract.dto.InterestCollectionRequest;
import com.pawnmaster.api.modules.contract.dto.InterestPaymentResponse;
import com.pawnmaster.api.modules.contract.dto.LiquidationRequest;
import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import com.pawnmaster.api.modules.customer.CustomerEntity;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.user.RoleEnum;
import com.pawnmaster.api.modules.user.UserEntity;
import com.pawnmaster.api.modules.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Checkpoint 3: Contract Lifecycle, Servicing & Calculations Tests")
public class ContractLifecycleServiceTest {

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private InterestPaymentRepository interestPaymentRepository;

    @Mock
    private CollateralRepository collateralRepository;

    @Mock
    private CashbookService cashbookService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ContractServiceImpl contractService;

    private BranchEntity sampleBranch;
    private UserEntity sampleStaff;
    private CustomerEntity sampleCustomer;
    private CollateralEntity sampleCollateral;
    private ContractEntity sampleActiveContract;

    @BeforeEach
    void setUp() {
        sampleBranch = BranchEntity.builder()
                .branchCode("CN-HCM-01")
                .branchName("Chi nhánh Quận 1")
                .provinceCode("79")
                .districtCode("760")
                .currentCashBalance(new BigDecimal("500000000.00"))
                .build();
        sampleBranch.setId(1L);

        sampleStaff = UserEntity.builder()
                .username("staff_01")
                .fullName("Nhân viên Thu tiền")
                .role(RoleEnum.STAFF)
                .branch(sampleBranch)
                .build();
        sampleStaff.setId(10L);

        sampleCustomer = CustomerEntity.builder()
                .customerCode("KH-001")
                .fullName("Lê Văn Vay")
                .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                .branch(sampleBranch)
                .build();
        sampleCustomer.setId(100L);

        sampleCollateral = CollateralEntity.builder()
                .collateralCode("TS-001")
                .customer(sampleCustomer)
                .branch(sampleBranch)
                .name("MacBook Pro M2")
                .category(CollateralCategory.LAPTOP)
                .appraisedValue(new BigDecimal("30000000.00"))
                .status(CollateralStatus.IN_VAULT)
                .build();
        sampleCollateral.setId(200L);

        sampleActiveContract = ContractEntity.builder()
                .contractCode("HD-2026-0001")
                .customer(sampleCustomer)
                .collateral(sampleCollateral)
                .branch(sampleBranch)
                .principalAmount(new BigDecimal("20000000.00"))
                .remainingPrincipal(new BigDecimal("20000000.00"))
                .interestType(InterestType.DAILY_PER_MILLION)
                .interestRate(new BigDecimal("2000.00"))
                .storageFeeRate(new BigDecimal("500.00"))
                .loanTermDays(30)
                .paymentCycleDays(10)
                .startDate(LocalDate.of(2026, 11, 1))
                .maturityDate(LocalDate.of(2026, 12, 1))
                .nextInterestDueDate(LocalDate.of(2026, 11, 11))
                .totalInterestPaid(BigDecimal.ZERO)
                .status(ContractStatus.ACTIVE)
                .build();
        sampleActiveContract.setId(500L);
    }

    // =========================================================================
    // 1. 3 INTEREST CALCULATION MODES
    // =========================================================================

    @Nested
    @DisplayName("1. Interest Calculation Mode Tests")
    class InterestCalculationTests {

        @Test
        @DisplayName("Mode 1: DAILY_PER_MILLION - (principal / 1,000,000) * ratePerDay * days")
        void calculateInterest_DailyPerMillion() {
            // Principal: 20M, Rate: 2000/triệu/ngày, Storage: 500/triệu/ngày, Days: 10
            // Interest = (20,000,000 / 1,000,000) * 2,000 * 10 = 20 * 2000 * 10 = 400,000
            // Storage Fee = (20,000,000 / 1,000,000) * 500 * 10 = 20 * 500 * 10 = 100,000
            // Total = 500,000
            LocalDate fromDate = LocalDate.of(2026, 11, 1);
            LocalDate toDate = LocalDate.of(2026, 11, 11); // 10 days

            InterestCalculationResponse res = contractService.calculateInterest(sampleActiveContract, fromDate, toDate);

            assertNotNull(res);
            assertEquals(10, res.getDaysCount());
            assertEquals(new BigDecimal("400000.00"), res.getInterestAmount().setScale(2, RoundingMode.HALF_UP));
            assertEquals(new BigDecimal("100000.00"), res.getStorageFeeAmount().setScale(2, RoundingMode.HALF_UP));
            assertEquals(new BigDecimal("500000.00"), res.getTotalDue().setScale(2, RoundingMode.HALF_UP));
        }

        @Test
        @DisplayName("Mode 2: PERCENT_MONTHLY - principal * (rateMonthly / 100) * (days / 30)")
        void calculateInterest_PercentMonthly() {
            sampleActiveContract.setInterestType(InterestType.PERCENT_MONTHLY);
            sampleActiveContract.setInterestRate(new BigDecimal("3.00")); // 3% / tháng
            sampleActiveContract.setStorageFeeRate(BigDecimal.ZERO);

            // Case A: Đủ 30 ngày -> 20M * 3% * 1 = 600,000
            LocalDate fromDate = LocalDate.of(2026, 11, 1);
            LocalDate toDate30 = LocalDate.of(2026, 12, 1); // 30 days
            InterestCalculationResponse res30 = contractService.calculateInterest(sampleActiveContract, fromDate, toDate30);
            assertEquals(30, res30.getDaysCount());
            assertEquals(new BigDecimal("600000.00"), res30.getInterestAmount().setScale(2, RoundingMode.HALF_UP));

            // Case B: 15 ngày -> 20M * 3% * (15/30) = 300,000
            LocalDate toDate15 = LocalDate.of(2026, 11, 16); // 15 days
            InterestCalculationResponse res15 = contractService.calculateInterest(sampleActiveContract, fromDate, toDate15);
            assertEquals(15, res15.getDaysCount());
            assertEquals(new BigDecimal("300000.00"), res15.getInterestAmount().setScale(2, RoundingMode.HALF_UP));
        }

        @Test
        @DisplayName("Mode 3: PERCENT_WEEKLY - principal * (rateWeekly / 100) * (days / 7)")
        void calculateInterest_PercentWeekly() {
            sampleActiveContract.setInterestType(InterestType.PERCENT_WEEKLY);
            sampleActiveContract.setInterestRate(new BigDecimal("1.00")); // 1% / tuần
            sampleActiveContract.setStorageFeeRate(BigDecimal.ZERO);

            // Case A: 7 ngày -> 20M * 1% * (7/7) = 200,000
            LocalDate fromDate = LocalDate.of(2026, 11, 1);
            LocalDate toDate7 = LocalDate.of(2026, 11, 8); // 7 days
            InterestCalculationResponse res7 = contractService.calculateInterest(sampleActiveContract, fromDate, toDate7);
            assertEquals(7, res7.getDaysCount());
            assertEquals(new BigDecimal("200000.00"), res7.getInterestAmount().setScale(2, RoundingMode.HALF_UP));

            // Case B: 14 ngày -> 20M * 1% * (14/7) = 400,000
            LocalDate toDate14 = LocalDate.of(2026, 11, 15); // 14 days
            InterestCalculationResponse res14 = contractService.calculateInterest(sampleActiveContract, fromDate, toDate14);
            assertEquals(14, res14.getDaysCount());
            assertEquals(new BigDecimal("400000.00"), res14.getInterestAmount().setScale(2, RoundingMode.HALF_UP));
        }
    }

    // =========================================================================
    // 2. PERIODIC COLLECTION & PARTIAL PRINCIPAL
    // =========================================================================

    @Test
    @DisplayName("collectInterest - Thu lãi định kỳ: Tăng totalInterestPaid, dời kỳ hạn đóng lãi, ghi CASH_IN vào sổ quỹ")
    void collectInterest_PeriodicPayment_AdvancesDueDate_RecordsCashIn() {
        when(contractRepository.findByIdAndDeletedFalse(500L)).thenReturn(Optional.of(sampleActiveContract));
        when(interestPaymentRepository.save(any(InterestPaymentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cashbookService.collectCashIn(any(), any(), any(TransactionCategory.class), any(), any(), any()))
                .thenReturn(CashbookResponse.builder().build());

        InterestCollectionRequest req = InterestCollectionRequest.builder()
                .interestAmount(new BigDecimal("400000.00"))
                .storageFeeAmount(new BigDecimal("100000.00"))
                .penaltyAmount(BigDecimal.ZERO)
                .principalPaidAmount(BigDecimal.ZERO)
                .paymentMethod("CASH")
                .notes("Đóng lãi kỳ 1")
                .build();

        InterestPaymentResponse res = contractService.collectInterest(500L, req, sampleStaff.getId());

        assertNotNull(res);
        // totalInterestPaid tăng đúng 500,000 (lãi + phí lưu kho)
        assertEquals(new BigDecimal("500000.00"), sampleActiveContract.getTotalInterestPaid());
        // Gốc còn lại giữ nguyên 20,000,000
        assertEquals(new BigDecimal("20000000.00"), sampleActiveContract.getRemainingPrincipal());
        // Ngày đóng lãi tiếp theo được dời thêm 10 ngày (từ 11/11 thành 21/11)
        assertEquals(LocalDate.of(2026, 11, 21), sampleActiveContract.getNextInterestDueDate());

        // Verify ghi sổ quỹ CASH_IN
        verify(cashbookService, times(1)).collectCashIn(
                eq(1L),
                eq(new BigDecimal("500000.00")),
                eq(TransactionCategory.INTEREST_COLLECTION),
                eq(500L),
                eq(sampleStaff.getId()),
                anyString()
        );
        verify(contractRepository, times(1)).save(sampleActiveContract);
    }

    @Test
    @DisplayName("collectInterest - Trả bớt gốc: Giảm remainingPrincipal và lưu đúng loại thanh toán PARTIAL_PRINCIPAL")
    void collectInterest_WithPartialPrincipal_DecrementsRemainingPrincipal() {
        when(contractRepository.findByIdAndDeletedFalse(500L)).thenReturn(Optional.of(sampleActiveContract));
        when(interestPaymentRepository.save(any(InterestPaymentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        InterestCollectionRequest req = InterestCollectionRequest.builder()
                .interestAmount(new BigDecimal("400000.00"))
                .storageFeeAmount(new BigDecimal("100000.00"))
                .penaltyAmount(BigDecimal.ZERO)
                .principalPaidAmount(new BigDecimal("5000000.00")) // Trả bớt 5M gốc
                .paymentMethod("CASH")
                .notes("Đóng lãi + trả bớt gốc 5M")
                .build();

        contractService.collectInterest(500L, req, sampleStaff.getId());

        // Gốc còn lại giảm từ 20M xuống 15M
        assertEquals(new BigDecimal("15000000.00"), sampleActiveContract.getRemainingPrincipal());
        assertEquals(new BigDecimal("500000.00"), sampleActiveContract.getTotalInterestPaid());

        // Verify InterestPaymentEntity lưu loại PARTIAL_PRINCIPAL
        ArgumentCaptor<InterestPaymentEntity> captor = ArgumentCaptor.forClass(InterestPaymentEntity.class);
        verify(interestPaymentRepository, times(1)).save(captor.capture());
        InterestPaymentEntity savedPayment = captor.getValue();
        assertEquals(PaymentType.PARTIAL_PRINCIPAL, savedPayment.getPaymentType());
        assertEquals(new BigDecimal("5000000.00"), savedPayment.getPrincipalPaidAmount());
        assertEquals(new BigDecimal("5500000.00"), savedPayment.getTotalAmount()); // 500k lãi + 5M gốc
    }

    @Test
    @DisplayName("collectInterest - Ném INVALID_REQUEST khi số tiền trả gốc vượt quá gốc còn lại")
    void collectInterest_PrincipalPaidExceedsRemaining_ThrowsException() {
        when(contractRepository.findByIdAndDeletedFalse(500L)).thenReturn(Optional.of(sampleActiveContract));

        InterestCollectionRequest req = InterestCollectionRequest.builder()
                .interestAmount(new BigDecimal("400000.00"))
                .principalPaidAmount(new BigDecimal("25000000.00")) // Đòi trả 25M trong khi gốc chỉ còn 20M
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                contractService.collectInterest(500L, req, sampleStaff.getId()));

        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
        verify(cashbookService, never()).collectCashIn(any(), any(), any(TransactionCategory.class), any(), any(), any());
    }

    // =========================================================================
    // 3. CONTRACT REDEMPTION (CHUỘC ĐỒ)
    // =========================================================================

    @Test
    @DisplayName("redeemContract - Tất toán chuộc đồ: remainingPrincipal = 0, HĐ REDEEMED, tài sản REDEEMED, ghi CASH_IN")
    void redeemContract_FullPayoff_SetsStatusRedeemed_RecordsCashIn() {
        when(contractRepository.findByIdAndDeletedFalse(500L)).thenReturn(Optional.of(sampleActiveContract));
        when(cashbookService.collectCashIn(any(), any(), any(TransactionCategory.class), any(), any(), any()))
                .thenReturn(CashbookResponse.builder().build());

        ContractResponse response = contractService.redeemContract(500L, sampleStaff.getId(), "Khách tất toán chuộc đồ");

        assertNotNull(response);
        assertEquals(ContractStatus.REDEEMED, sampleActiveContract.getStatus());
        assertEquals(BigDecimal.ZERO.setScale(2), sampleActiveContract.getRemainingPrincipal().setScale(2));
        assertNotNull(sampleActiveContract.getClosedDate());
        assertEquals(CollateralStatus.REDEEMED, sampleCollateral.getStatus());

        // Verify Sổ quỹ ghi nhận thu gốc tất toán
        verify(cashbookService, times(1)).collectCashIn(
                eq(1L),
                eq(new BigDecimal("20000000.00")),
                eq(TransactionCategory.PRINCIPAL_COLLECTION),
                eq(500L),
                eq(sampleStaff.getId()),
                anyString()
        );
        verify(contractRepository, times(1)).save(sampleActiveContract);
        verify(collateralRepository, times(1)).save(sampleCollateral);
    }

    @Test
    @DisplayName("redeemContract - Ném CONTRACT_ALREADY_CLOSED khi hợp đồng đã chuộc hoặc đã thanh lý")
    void redeemContract_AlreadyClosed_ThrowsException() {
        sampleActiveContract.setStatus(ContractStatus.REDEEMED);
        when(contractRepository.findByIdAndDeletedFalse(500L)).thenReturn(Optional.of(sampleActiveContract));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                contractService.redeemContract(500L, sampleStaff.getId(), "Chuộc lại"));

        assertEquals(ErrorCode.CONTRACT_ALREADY_CLOSED, ex.getErrorCode());
        verify(cashbookService, never()).collectCashIn(any(), any(), any(TransactionCategory.class), any(), any(), any());
    }

    // =========================================================================
    // 4. DEFAULT & LIQUIDATION (THANH LÝ TÀI SẢN)
    // =========================================================================

    @Test
    @DisplayName("liquidateContract - Thanh lý: HĐ LIQUIDATED, tài sản LIQUIDATED, ghi thu LIQUIDATION_INCOME")
    void liquidateContract_Success_SetsStatusLiquidated_RecordsLiquidationIncome() {
        sampleActiveContract.setStatus(ContractStatus.OVERDUE);
        sampleCollateral.setStatus(CollateralStatus.WAITING_LIQUIDATION);
        when(contractRepository.findByIdAndDeletedFalse(500L)).thenReturn(Optional.of(sampleActiveContract));
        when(cashbookService.collectCashIn(any(), any(), any(TransactionCategory.class), any(), any(), any()))
                .thenReturn(CashbookResponse.builder().build());

        LiquidationRequest req = LiquidationRequest.builder()
                .liquidationAmount(new BigDecimal("22000000.00")) // Bán thanh lý được 22M
                .notes("Bán thanh lý laptop cho người dùng cuối")
                .build();

        ContractResponse response = contractService.liquidateContract(500L, req, sampleStaff.getId());

        assertNotNull(response);
        assertEquals(ContractStatus.LIQUIDATED, sampleActiveContract.getStatus());
        assertEquals(BigDecimal.ZERO.setScale(2), sampleActiveContract.getRemainingPrincipal().setScale(2));
        assertNotNull(sampleActiveContract.getClosedDate());
        assertEquals(CollateralStatus.LIQUIDATED, sampleCollateral.getStatus());

        verify(cashbookService, times(1)).collectCashIn(
                eq(1L),
                eq(new BigDecimal("22000000.00")),
                eq(TransactionCategory.LIQUIDATION_INCOME),
                eq(500L),
                eq(sampleStaff.getId()),
                anyString()
        );
        verify(contractRepository, times(1)).save(sampleActiveContract);
        verify(collateralRepository, times(1)).save(sampleCollateral);
    }

    // =========================================================================
    // 5. MARK OVERDUE CONTRACTS (QUÁ HẠN HỢP ĐỒNG)
    // =========================================================================

    @Test
    @DisplayName("markOverdueContracts - Chuyển sang OVERDUE đối với hợp đồng ACTIVE quá ngày đáo hạn")
    void markOverdueContracts_PastMaturityDate_TransitionsToOverdue() {
        // HĐ 1: Quá hạn đáo hạn (maturityDate = hôm qua) -> chuyển OVERDUE
        ContractEntity overdueContract = ContractEntity.builder()
                .status(ContractStatus.ACTIVE)
                .maturityDate(LocalDate.now().minusDays(1))
                .collateral(sampleCollateral)
                .build();
        overdueContract.setId(501L);

        // HĐ 2: Chưa đến hạn (maturityDate = 5 ngày nữa) -> giữ nguyên ACTIVE
        ContractEntity activeContract = ContractEntity.builder()
                .status(ContractStatus.ACTIVE)
                .maturityDate(LocalDate.now().plusDays(5))
                .collateral(CollateralEntity.builder().status(CollateralStatus.IN_VAULT).build())
                .build();
        activeContract.setId(502L);

        when(contractRepository.findByStatusAndDeletedFalse(ContractStatus.ACTIVE))
                .thenReturn(List.of(overdueContract, activeContract));

        int markedCount = contractService.markOverdueContracts();

        assertEquals(1, markedCount);
        assertEquals(ContractStatus.OVERDUE, overdueContract.getStatus());
        assertEquals(CollateralStatus.WAITING_LIQUIDATION, overdueContract.getCollateral().getStatus());
        assertEquals(ContractStatus.ACTIVE, activeContract.getStatus());

        verify(contractRepository, times(1)).save(overdueContract);
        verify(contractRepository, never()).save(activeContract);
    }

    @Test
    @DisplayName("getDashboardMonthlyContracts - Truy vấn đúng khoảng đầu/cuối tháng và các trạng thái thu tiền")
    void getDashboardMonthlyContracts_QueriesMonthRangeAndOpenStatuses() {
        YearMonth targetMonth = YearMonth.of(2026, 11);
        LocalDate expectedStart = LocalDate.of(2026, 11, 1);
        LocalDate expectedEnd = LocalDate.of(2026, 11, 30);
        List<ContractStatus> expectedStatuses = List.of(
                ContractStatus.ACTIVE,
                ContractStatus.EXTENDED,
                ContractStatus.OVERDUE
        );

        when(contractRepository.findDashboardMonthlyContracts(
                eq(null),
                eq(expectedStart),
                eq(expectedEnd),
                eq(expectedStatuses)
        )).thenReturn(List.of(sampleActiveContract));

        List<ContractResponse> result = contractService.getDashboardMonthlyContracts(null, targetMonth);

        assertEquals(1, result.size());
        assertEquals("HD-2026-0001", result.get(0).getContractCode());
        verify(contractRepository, times(1)).findDashboardMonthlyContracts(
                eq(null),
                eq(expectedStart),
                eq(expectedEnd),
                eq(expectedStatuses)
        );
    }
}

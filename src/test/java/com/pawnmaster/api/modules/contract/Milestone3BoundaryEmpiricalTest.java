package com.pawnmaster.api.modules.contract;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.branch.BranchRepository;
import com.pawnmaster.api.modules.cashbook.CashbookEntity;
import com.pawnmaster.api.modules.cashbook.CashbookRepository;
import com.pawnmaster.api.modules.cashbook.CashbookService;
import com.pawnmaster.api.modules.cashbook.CashbookServiceImpl;
import com.pawnmaster.api.modules.cashbook.TransactionCategory;
import com.pawnmaster.api.modules.cashbook.TransactionType;
import com.pawnmaster.api.modules.cashbook.dto.CashFlowSummaryResponse;
import com.pawnmaster.api.modules.cashbook.dto.CashbookCreateRequest;
import com.pawnmaster.api.modules.cashbook.dto.CashbookResponse;
import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.collateral.CollateralEntity;
import com.pawnmaster.api.modules.collateral.CollateralRepository;
import com.pawnmaster.api.modules.collateral.CollateralStatus;
import com.pawnmaster.api.modules.collateral.LtvPolicyService;
import com.pawnmaster.api.modules.customer.VerificationDocumentPolicy;
import com.pawnmaster.api.modules.contract.dto.InterestCalculationResponse;
import com.pawnmaster.api.modules.contract.dto.InterestCollectionRequest;
import com.pawnmaster.api.modules.contract.dto.InterestPaymentResponse;
import com.pawnmaster.api.modules.contract.dto.LiquidationRequest;
import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import com.pawnmaster.api.modules.customer.CustomerEntity;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.CustomerRepository;
import com.pawnmaster.api.modules.user.RoleEnum;
import com.pawnmaster.api.modules.user.UserEntity;
import com.pawnmaster.api.modules.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Milestone 3 Financial Operations & Boundary Verification Empirical Test
 * Suite.
 *
 * Designed and executed by Challenger 1 (M3 Financial & Boundary Challenger) to
 * empirically verify:
 * 1. Cash drawer boundary conditions:
 * - Exact balance disbursement: amount == currentCashBalance -> balanceAfter =
 * 0 (PASSES)
 * - Overdraft attempt: amount = currentCashBalance + 1 VND -> throws
 * INSUFFICIENT_CASH_BALANCE
 * - Overdraft attempt: amount = currentCashBalance * 2 -> throws
 * INSUFFICIENT_CASH_BALANCE
 * - Zero, negative, and null amount validations across disburseLoan,
 * collectCashIn, recordTransaction
 * - Cashbook recordTransaction CASH_OUT exact balance and overdraft checks
 * 2. Maker-Checker authorization boundary tests:
 * - STAFF attempt to approve -> throws FORBIDDEN
 * - ACCOUNTANT attempt to approve -> throws FORBIDDEN
 * - MANAGER from Branch 2 attempting to approve Branch 1 contract -> throws
 * FORBIDDEN
 * - MANAGER with null branch -> throws FORBIDDEN
 * - ADMIN approval of any branch -> PASSES
 * - Non-pending contract approval attempt (ACTIVE, CANCELLED, REDEEMED,
 * LIQUIDATED, OVERDUE) -> throws CONTRACT_NOT_PENDING_APPROVAL
 * - Rejection authorization & status validations (STAFF forbidden, Branch 2
 * Manager forbidden, ADMIN passes, non-pending rejected)
 * 3. Interest calculation mathematical edge cases:
 * - Odd loan days (1 day, 7 days, 17 days, 29 days, 30 days, 31 days, 43 days)
 * across DAILY_PER_MILLION, PERCENT_MONTHLY, PERCENT_WEEKLY
 * - Odd principal amounts and rounding precision (HALF_UP, 2 decimal places)
 * - Zero duration (0 days) and negative duration exception handling
 * 4. Contract servicing & principal settlement:
 * - Partial paydown exact subtraction: remainingPrincipal never negative
 * - Paydown exceeding remainingPrincipal -> throws INVALID_REQUEST
 * - Negative or zero paydown handling
 * - Full redemption: remainingPrincipal reaches exactly 0
 * - Closed contract protections (REDEEMED, LIQUIDATED, CANCELLED cannot be
 * serviced, redeemed, or liquidated)
 * - Liquidation recording & zero/negative amount rejection
 * - Overdue contract auto-detection based on maturityDate
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Milestone 3 Boundary & Financial Stress Empirical Challenge Suite")
public class Milestone3BoundaryEmpiricalTest {

        // =========================================================================
        // SECTION 1: CASH DRAWER BOUNDARY CONDITIONS
        // =========================================================================

        @Nested
        @DisplayName("1. Cash Drawer Boundary & Overdraft Empirical Tests")
        class CashDrawerBoundaryEmpiricalTests {

                @Mock
                private CashbookRepository cashbookRepository;

                @Mock
                private BranchRepository branchRepository;

                @Mock
                private UserRepository userRepository;

                @Mock
                private ContractRepository contractRepository;

                @Mock
                private CustomerRepository customerRepository;

                @InjectMocks
                private CashbookServiceImpl cashbookService;

                private BranchEntity branch;

                @BeforeEach
                void setUp() {
                        branch = BranchEntity.builder()
                                        .branchCode("CN-DRAWER-01")
                                        .branchName("Chi nhánh Quỹ Tiền Mặt")
                                        .currentCashBalance(new BigDecimal("100000000.00")) // 100M VND
                                        .capitalLimit(new BigDecimal("1000000000.00"))
                                        .active(true)
                                        .build();
                        branch.setId(10L);

                        lenient().when(branchRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(branch));
                        lenient().when(branchRepository.save(any(BranchEntity.class)))
                                        .thenAnswer(i -> i.getArgument(0));
                        lenient().when(cashbookRepository.save(any(CashbookEntity.class)))
                                        .thenAnswer(i -> i.getArgument(0));
                }

                @Test
                @DisplayName("Exact balance disbursement: amount == currentCashBalance -> balanceAfter = 0 (PASSES)")
                void testExactBalanceDisbursementReducesBalanceToZero() {
                        BigDecimal exactBalance = new BigDecimal("100000000.00");

                        CashbookResponse response = cashbookService.disburseLoan(10L, exactBalance, 100L, 1L,
                                        "Giải ngân đúng số dư");

                        assertNotNull(response);
                        assertEquals(0, BigDecimal.ZERO.compareTo(response.getBalanceAfter()),
                                        "Số dư sau giải ngân đúng bằng số dư hiện tại phải về đúng 0");
                        assertEquals(0, BigDecimal.ZERO.compareTo(branch.getCurrentCashBalance()),
                                        "Chi nhánh currentCashBalance phải về đúng 0");

                        ArgumentCaptor<BranchEntity> branchCaptor = ArgumentCaptor.forClass(BranchEntity.class);
                        verify(branchRepository).save(branchCaptor.capture());
                        assertEquals(0, BigDecimal.ZERO.compareTo(branchCaptor.getValue().getCurrentCashBalance()));

                        ArgumentCaptor<CashbookEntity> voucherCaptor = ArgumentCaptor.forClass(CashbookEntity.class);
                        verify(cashbookRepository).save(voucherCaptor.capture());
                        assertEquals(TransactionType.CASH_OUT, voucherCaptor.getValue().getTransactionType());
                        assertEquals(TransactionCategory.DISBURSEMENT, voucherCaptor.getValue().getCategory());
                        assertEquals(0, BigDecimal.ZERO.compareTo(voucherCaptor.getValue().getBalanceAfter()));
                }

                @Test
                @DisplayName("Overdraft attempt by exactly 1 VND: amount = currentCashBalance + 1 -> throws INSUFFICIENT_CASH_BALANCE")
                void testOverdraftAttemptByOneVndThrowsInsufficientCashBalance() {
                        BigDecimal overdraftAmount = branch.getCurrentCashBalance().add(BigDecimal.ONE); // 100,000,001
                                                                                                         // VND

                        BusinessException ex = assertThrows(BusinessException.class, () -> cashbookService
                                        .disburseLoan(10L, overdraftAmount, 100L, 1L, "Cố tình xuất quá quỹ 1 VND"));

                        assertEquals(ErrorCode.INSUFFICIENT_CASH_BALANCE, ex.getErrorCode());
                        // Verify branch balance is untouched
                        assertEquals(0, new BigDecimal("100000000.00").compareTo(branch.getCurrentCashBalance()));
                        verify(cashbookRepository, never()).save(any(CashbookEntity.class));
                }

                @Test
                @DisplayName("Overdraft attempt by 0.01 fractional currency unit -> throws INSUFFICIENT_CASH_BALANCE")
                void testOverdraftAttemptByFractionalAmountThrowsInsufficientCashBalance() {
                        BigDecimal overdraftAmount = branch.getCurrentCashBalance().add(new BigDecimal("0.01"));

                        BusinessException ex = assertThrows(BusinessException.class, () -> cashbookService
                                        .disburseLoan(10L, overdraftAmount, 100L, 1L, "Cố tình xuất quá quỹ 0.01 VND"));

                        assertEquals(ErrorCode.INSUFFICIENT_CASH_BALANCE, ex.getErrorCode());
                        verify(cashbookRepository, never()).save(any(CashbookEntity.class));
                }

                @Test
                @DisplayName("Massive overdraft attempt: amount = currentCashBalance * 2 -> throws INSUFFICIENT_CASH_BALANCE")
                void testMassiveOverdraftThrowsInsufficientCashBalance() {
                        BigDecimal massiveAmount = branch.getCurrentCashBalance().multiply(new BigDecimal("2"));

                        BusinessException ex = assertThrows(BusinessException.class, () -> cashbookService
                                        .disburseLoan(10L, massiveAmount, 100L, 1L, "Cố tình xuất gấp đôi quỹ"));

                        assertEquals(ErrorCode.INSUFFICIENT_CASH_BALANCE, ex.getErrorCode());
                        verify(cashbookRepository, never()).save(any(CashbookEntity.class));
                }

                @ParameterizedTest(name = "Disburse non-positive amount {0} throws INVALID_REQUEST")
                @ValueSource(strings = { "0", "0.00", "-1", "-100000", "-999999999" })
                void testDisburseZeroOrNegativeAmountThrowsInvalidRequest(String invalidAmountStr) {
                        BigDecimal invalidAmount = new BigDecimal(invalidAmountStr);

                        BusinessException ex = assertThrows(BusinessException.class, () -> cashbookService
                                        .disburseLoan(10L, invalidAmount, 100L, 1L, "Số tiền không hợp lệ"));

                        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
                        verify(branchRepository, never()).save(any(BranchEntity.class));
                        verify(cashbookRepository, never()).save(any(CashbookEntity.class));
                }

                @Test
                @DisplayName("Disburse with null amount throws INVALID_REQUEST")
                void testDisburseNullAmountThrowsInvalidRequest() {
                        BusinessException ex = assertThrows(BusinessException.class,
                                        () -> cashbookService.disburseLoan(10L, null, 100L, 1L, "Số tiền null"));

                        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
                        verify(cashbookRepository, never()).save(any(CashbookEntity.class));
                }

                @Test
                @DisplayName("Collect cash in with exact addition: new balance = currentCashBalance + amount")
                void testCollectCashInExactAddition() {
                        BigDecimal deposit = new BigDecimal("25500000.50");
                        BigDecimal expectedBalance = branch.getCurrentCashBalance().add(deposit);

                        CashbookResponse response = cashbookService.collectCashIn(10L, deposit,
                                        TransactionCategory.INTEREST_COLLECTION,
                                        100L, 1L, "Thu tiền lãi chuẩn xác");

                        assertNotNull(response);
                        assertEquals(0, expectedBalance.compareTo(response.getBalanceAfter()));
                        assertEquals(0, expectedBalance.compareTo(branch.getCurrentCashBalance()));
                        assertEquals(TransactionType.CASH_IN, response.getTransactionType());
                }

                @ParameterizedTest(name = "Collect non-positive cash in amount {0} throws INVALID_REQUEST")
                @ValueSource(strings = { "0", "0.00", "-1", "-500000" })
                void testCollectCashInZeroOrNegativeAmountThrowsInvalidRequest(String invalidAmountStr) {
                        BigDecimal invalidAmount = new BigDecimal(invalidAmountStr);

                        BusinessException ex = assertThrows(BusinessException.class,
                                        () -> cashbookService.collectCashIn(10L, invalidAmount,
                                                        TransactionCategory.INTEREST_COLLECTION,
                                                        100L, 1L, "Thu tiền âm"));

                        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
                        verify(branchRepository, never()).save(any(BranchEntity.class));
                }

                @Test
                @DisplayName("Cashbook recordTransaction generic CASH_OUT exact balance and overdraft verification")
                void testRecordTransactionGenericCashOutBoundary() {
                        // 1. Exact balance via recordTransaction
                        CashbookCreateRequest exactReq = CashbookCreateRequest.builder()
                                        .branchId(10L)
                                        .transactionType(TransactionType.CASH_OUT)
                                        .category(TransactionCategory.OPERATING_EXPENSE)
                                        .amount(new BigDecimal("100000000.00"))
                                        .description("Chi toàn bộ số dư")
                                        .build();

                        CashbookResponse exactResp = cashbookService.recordTransaction(exactReq);
                        assertEquals(0, BigDecimal.ZERO.compareTo(exactResp.getBalanceAfter()));
                        assertEquals(0, BigDecimal.ZERO.compareTo(branch.getCurrentCashBalance()));

                        // 2. Overdraft now that balance is 0
                        CashbookCreateRequest overReq = CashbookCreateRequest.builder()
                                        .branchId(10L)
                                        .transactionType(TransactionType.CASH_OUT)
                                        .category(TransactionCategory.OPERATING_EXPENSE)
                                        .amount(BigDecimal.ONE)
                                        .description("Chi 1 VND khi quỹ đã về 0")
                                        .build();

                        BusinessException ex = assertThrows(BusinessException.class,
                                        () -> cashbookService.recordTransaction(overReq));
                        assertEquals(ErrorCode.INSUFFICIENT_CASH_BALANCE, ex.getErrorCode());
                }

                @Test
                @DisplayName("Branch not found when accessing non-existent branch ID throws BRANCH_NOT_FOUND")
                void testNonExistentBranchThrowsBranchNotFound() {
                        when(branchRepository.findByIdAndDeletedFalse(9999L)).thenReturn(Optional.empty());

                        BusinessException ex = assertThrows(BusinessException.class,
                                        () -> cashbookService.disburseLoan(9999L, new BigDecimal("1000000.00"), 100L,
                                                        1L, "Chi nhánh không tồn tại"));

                        assertEquals(ErrorCode.BRANCH_NOT_FOUND, ex.getErrorCode());
                }
        }

        // =========================================================================
        // SECTION 2: MAKER-CHECKER AUTHORIZATION BOUNDARY TESTS
        // =========================================================================

        @Nested
        @DisplayName("2. Maker-Checker Authorization & State Boundary Empirical Tests")
        class MakerCheckerAuthorizationBoundaryEmpiricalTests {

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

                @InjectMocks
                private ContractServiceImpl contractService;

                private BranchEntity branch1;
                private BranchEntity branch2;
                private UserEntity staffBranch1;
                private UserEntity accountantBranch1;
                private UserEntity managerBranch1;
                private UserEntity managerBranch2;
                private UserEntity adminUser;
                private ContractEntity pendingContract;
                private CollateralEntity collateral;

                @BeforeEach
                void setUp() {
                        branch1 = BranchEntity.builder()
                                        .branchCode("CN-B1")
                                        .branchName("Chi nhánh 1")
                                        .currentCashBalance(new BigDecimal("500000000.00"))
                                        .build();
                        branch1.setId(1L);

                        branch2 = BranchEntity.builder()
                                        .branchCode("CN-B2")
                                        .branchName("Chi nhánh 2")
                                        .currentCashBalance(new BigDecimal("500000000.00"))
                                        .build();
                        branch2.setId(2L);

                        staffBranch1 = UserEntity.builder()
                                        .username("staff_b1")
                                        .role(RoleEnum.STAFF)
                                        .branch(branch1)
                                        .build();
                        staffBranch1.setId(101L);

                        accountantBranch1 = UserEntity.builder()
                                        .username("acct_b1")
                                        .role(RoleEnum.ACCOUNTANT)
                                        .branch(branch1)
                                        .build();
                        accountantBranch1.setId(102L);

                        managerBranch1 = UserEntity.builder()
                                        .username("manager_b1")
                                        .role(RoleEnum.MANAGER)
                                        .branch(branch1)
                                        .build();
                        managerBranch1.setId(103L);

                        managerBranch2 = UserEntity.builder()
                                        .username("manager_b2")
                                        .role(RoleEnum.MANAGER)
                                        .branch(branch2)
                                        .build();
                        managerBranch2.setId(104L);

                        adminUser = UserEntity.builder()
                                        .username("admin_root")
                                        .role(RoleEnum.ADMIN)
                                        .branch(null) // ADMIN may not have a branch
                                        .build();
                        adminUser.setId(1L);

                        collateral = CollateralEntity.builder()
                                        .collateralCode("TS-001")
                                        .category(CollateralCategory.MOTORBIKE)
                                        .appraisedValue(new BigDecimal("50000000.00"))
                                        .status(CollateralStatus.IN_VAULT)
                                        .branch(branch1)
                                        .build();
                        collateral.setId(201L);

                        pendingContract = ContractEntity.builder()
                                        .contractCode("HD-PENDING-001")
                                        .branch(branch1)
                                        .collateral(collateral)
                                        .principalAmount(new BigDecimal("30000000.00"))
                                        .remainingPrincipal(new BigDecimal("30000000.00"))
                                        .status(ContractStatus.PENDING_APPROVAL)
                                        .interestType(InterestType.DAILY_PER_MILLION)
                                        .interestRate(new BigDecimal("2000.00"))
                                        .storageFeeRate(new BigDecimal("1000.00"))
                                        .loanTermDays(30)
                                        .paymentCycleDays(10)
                                        .startDate(LocalDate.now())
                                        .maturityDate(LocalDate.now().plusDays(30))
                                        .nextInterestDueDate(LocalDate.now().plusDays(10))
                                        .totalInterestPaid(BigDecimal.ZERO)
                                        .build();
                        pendingContract.setId(501L);

                        lenient().when(contractRepository.findByIdAndDeletedFalse(501L))
                                        .thenReturn(Optional.of(pendingContract));
                        lenient().when(contractRepository.save(any(ContractEntity.class)))
                                        .thenAnswer(i -> i.getArgument(0));
                        lenient().when(collateralRepository.save(any(CollateralEntity.class)))
                                        .thenAnswer(i -> i.getArgument(0));
                }

                @Test
                @DisplayName("STAFF attempt to approve contract -> throws FORBIDDEN")
                void testStaffAttemptToApproveThrowsForbidden() {
                        when(userRepository.findByIdAndDeletedFalse(101L)).thenReturn(Optional.of(staffBranch1));

                        BusinessException ex = assertThrows(BusinessException.class, () -> contractService
                                        .approveAndDisburseContract(501L, 101L, "Staff tự phê duyệt"));

                        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
                        verify(cashbookService, never()).disburseLoan(any(), any(), any(), any(), any());
                        assertEquals(ContractStatus.PENDING_APPROVAL, pendingContract.getStatus());
                }

                @Test
                @DisplayName("ACCOUNTANT attempt to approve contract -> throws FORBIDDEN")
                void testAccountantAttemptToApproveThrowsForbidden() {
                        when(userRepository.findByIdAndDeletedFalse(102L)).thenReturn(Optional.of(accountantBranch1));

                        BusinessException ex = assertThrows(BusinessException.class, () -> contractService
                                        .approveAndDisburseContract(501L, 102L, "Kế toán tự phê duyệt"));

                        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
                        verify(cashbookService, never()).disburseLoan(any(), any(), any(), any(), any());
                        assertEquals(ContractStatus.PENDING_APPROVAL, pendingContract.getStatus());
                }

                @Test
                @DisplayName("MANAGER from Branch 2 attempting to approve Branch 1 contract -> throws FORBIDDEN")
                void testCrossBranchManagerApprovalThrowsForbidden() {
                        when(userRepository.findByIdAndDeletedFalse(104L)).thenReturn(Optional.of(managerBranch2));

                        BusinessException ex = assertThrows(BusinessException.class, () -> contractService
                                        .approveAndDisburseContract(501L, 104L, "Quản lý chi nhánh khác duyệt"));

                        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
                        verify(cashbookService, never()).disburseLoan(any(), any(), any(), any(), any());
                        assertEquals(ContractStatus.PENDING_APPROVAL, pendingContract.getStatus());
                }

                @Test
                @DisplayName("MANAGER from Branch 1 approving Branch 1 contract -> PASSES (Maker-Checker success)")
                void testSameBranchManagerApprovalPasses() {
                        when(userRepository.findByIdAndDeletedFalse(103L)).thenReturn(Optional.of(managerBranch1));

                        ContractResponse resp = contractService.approveAndDisburseContract(501L, 103L,
                                        "Quản lý CN1 duyệt hợp lệ");

                        assertNotNull(resp);
                        assertEquals(ContractStatus.ACTIVE, resp.getStatus());
                        assertEquals(ContractStatus.ACTIVE, pendingContract.getStatus());
                        assertEquals(CollateralStatus.IN_VAULT, collateral.getStatus());
                        assertEquals(103L, pendingContract.getApprovedBy());
                        assertNotNull(pendingContract.getApprovedAt());

                        verify(cashbookService).disburseLoan(
                                        eq(1L),
                                        eq(new BigDecimal("30000000.00")),
                                        eq(501L),
                                        eq(103L),
                                        eq("Quản lý CN1 duyệt hợp lệ"));
                }

                @Test
                @DisplayName("ADMIN approval of any branch contract without branch binding -> PASSES")
                void testAdminApprovalOfAnyBranchContractPasses() {
                        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(adminUser));

                        ContractResponse resp = contractService.approveAndDisburseContract(501L, 1L,
                                        "Admin duyệt vượt cấp");

                        assertNotNull(resp);
                        assertEquals(ContractStatus.ACTIVE, resp.getStatus());
                        assertEquals(ContractStatus.ACTIVE, pendingContract.getStatus());
                        assertEquals(CollateralStatus.IN_VAULT, collateral.getStatus());

                        verify(cashbookService).disburseLoan(
                                        eq(1L),
                                        eq(new BigDecimal("30000000.00")),
                                        eq(501L),
                                        eq(1L),
                                        eq("Admin duyệt vượt cấp"));
                }

                @ParameterizedTest(name = "Approval of non-pending contract in status {0} throws CONTRACT_NOT_PENDING_APPROVAL")
                @EnumSource(value = ContractStatus.class, names = { "ACTIVE", "CANCELLED", "REDEEMED", "LIQUIDATED",
                                "OVERDUE" })
                void testApprovalOfNonPendingContractThrowsContractNotPendingApproval(ContractStatus nonPendingStatus) {
                        pendingContract.setStatus(nonPendingStatus);
                        when(userRepository.findByIdAndDeletedFalse(103L)).thenReturn(Optional.of(managerBranch1));

                        BusinessException ex = assertThrows(BusinessException.class, () -> contractService
                                        .approveAndDisburseContract(501L, 103L, "Cố tình duyệt lại hợp đồng"));

                        assertEquals(ErrorCode.CONTRACT_NOT_PENDING_APPROVAL, ex.getErrorCode());
                        verify(cashbookService, never()).disburseLoan(any(), any(), any(), any(), any());
                }

                @Test
                @DisplayName("STAFF attempt to reject contract -> throws FORBIDDEN")
                void testStaffAttemptToRejectThrowsForbidden() {
                        when(userRepository.findByIdAndDeletedFalse(101L)).thenReturn(Optional.of(staffBranch1));

                        BusinessException ex = assertThrows(BusinessException.class,
                                        () -> contractService.rejectContract(501L, 101L, "Nhân viên tự từ chối"));

                        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
                        assertEquals(ContractStatus.PENDING_APPROVAL, pendingContract.getStatus());
                }

                @Test
                @DisplayName("MANAGER from Branch 2 attempting to reject Branch 1 contract -> throws FORBIDDEN")
                void testCrossBranchManagerRejectionThrowsForbidden() {
                        when(userRepository.findByIdAndDeletedFalse(104L)).thenReturn(Optional.of(managerBranch2));

                        BusinessException ex = assertThrows(BusinessException.class, () -> contractService
                                        .rejectContract(501L, 104L, "Quản lý chi nhánh khác từ chối"));

                        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
                        assertEquals(ContractStatus.PENDING_APPROVAL, pendingContract.getStatus());
                }

                @Test
                @DisplayName("MANAGER from Branch 1 rejects Branch 1 contract -> PASSES (CANCELLED, zero cash movement)")
                void testSameBranchManagerRejectionPassesWithZeroCashMovement() {
                        when(userRepository.findByIdAndDeletedFalse(103L)).thenReturn(Optional.of(managerBranch1));

                        ContractResponse resp = contractService.rejectContract(501L, 103L, "Hồ sơ không đạt yêu cầu");

                        assertNotNull(resp);
                        assertEquals(ContractStatus.CANCELLED, resp.getStatus());
                        assertEquals(ContractStatus.CANCELLED, pendingContract.getStatus());
                        assertEquals("Hồ sơ không đạt yêu cầu", pendingContract.getApprovalNotes());
                        assertNotNull(pendingContract.getClosedDate());

                        // Strict verification: zero cash movement
                        verify(cashbookService, never()).disburseLoan(any(), any(), any(), any(), any());
                        verify(cashbookService, never()).collectCashIn(any(), any(), any(TransactionCategory.class),
                                        any(), any(), any());
                }

                @ParameterizedTest(name = "Rejection of non-pending contract in status {0} throws CONTRACT_NOT_PENDING_APPROVAL")
                @EnumSource(value = ContractStatus.class, names = { "ACTIVE", "CANCELLED", "REDEEMED", "LIQUIDATED",
                                "OVERDUE" })
                void testRejectionOfNonPendingContractThrowsContractNotPendingApproval(
                                ContractStatus nonPendingStatus) {
                        pendingContract.setStatus(nonPendingStatus);
                        when(userRepository.findByIdAndDeletedFalse(103L)).thenReturn(Optional.of(managerBranch1));

                        BusinessException ex = assertThrows(BusinessException.class, () -> contractService
                                        .rejectContract(501L, 103L, "Từ chối hợp đồng không ở trạng thái chờ duyệt"));

                        assertEquals(ErrorCode.CONTRACT_NOT_PENDING_APPROVAL, ex.getErrorCode());
                }
        }

        // =========================================================================
        // SECTION 3: INTEREST CALCULATION MATHEMATICAL EDGE CASES
        // =========================================================================

        @Nested
        @DisplayName("3. Interest Calculation Mathematical Edge Cases & Precision Tests")
        class InterestCalculationMathematicalEdgeCasesEmpiricalTests {

                private ContractServiceImpl contractService;

                @BeforeEach
                void setUp() {
                        // pure calculation logic does not depend on database or mocks
                        contractService = new ContractServiceImpl(null, null, null, null, null, null, null, null);
                }

                @Test
                @DisplayName("DAILY_PER_MILLION: Odd loan days (17 days and 43 days) with storage fee exact formula check")
                void testDailyPerMillionOddDaysExactMath() {
                        BigDecimal principal = new BigDecimal("10000000.00"); // 10M VND
                        BigDecimal dailyInterestRate = new BigDecimal("2000.00"); // 2,000 VND / million / day
                        BigDecimal dailyStorageFeeRate = new BigDecimal("1000.00"); // 1,000 VND / million / day

                        ContractEntity contract = ContractEntity.builder()
                                        .principalAmount(principal)
                                        .remainingPrincipal(principal)
                                        .interestType(InterestType.DAILY_PER_MILLION)
                                        .interestRate(dailyInterestRate)
                                        .storageFeeRate(dailyStorageFeeRate)
                                        .build();
                        contract.setId(991L);

                        LocalDate startDate = LocalDate.of(2026, 1, 1);

                        // Case A: 17 days
                        // factor = 10,000,000 / 1,000,000 = 10.00000000
                        // interest = 10 * 2000 * 17 = 340,000.00 VND
                        // storage = 10 * 1000 * 17 = 170,000.00 VND
                        // totalDue = 510,000.00 VND
                        LocalDate date17Days = startDate.plusDays(17);
                        InterestCalculationResponse res17 = contractService.calculateInterest(contract, startDate,
                                        date17Days);
                        assertEquals(17, res17.getDaysCount());
                        assertEquals(new BigDecimal("340000.00"), res17.getInterestAmount());
                        assertEquals(new BigDecimal("170000.00"), res17.getStorageFeeAmount());
                        assertEquals(new BigDecimal("510000.00"), res17.getTotalDue());

                        // Case B: 43 days
                        // interest = 10 * 2000 * 43 = 860,000.00 VND
                        // storage = 10 * 1000 * 43 = 430,000.00 VND
                        // totalDue = 1,290,000.00 VND
                        LocalDate date43Days = startDate.plusDays(43);
                        InterestCalculationResponse res43 = contractService.calculateInterest(contract, startDate,
                                        date43Days);
                        assertEquals(43, res43.getDaysCount());
                        assertEquals(new BigDecimal("860000.00"), res43.getInterestAmount());
                        assertEquals(new BigDecimal("430000.00"), res43.getStorageFeeAmount());
                        assertEquals(new BigDecimal("1290000.00"), res43.getTotalDue());
                }

                @Test
                @DisplayName("DAILY_PER_MILLION: Odd principal amounts (e.g. 15,350,000 VND) across 17 odd days")
                void testDailyPerMillionOddPrincipalAmount() {
                        BigDecimal principal = new BigDecimal("15350000.00");
                        BigDecimal dailyInterestRate = new BigDecimal("1500.00");
                        BigDecimal dailyStorageFeeRate = new BigDecimal("500.00");

                        ContractEntity contract = ContractEntity.builder()
                                        .principalAmount(principal)
                                        .remainingPrincipal(principal)
                                        .interestType(InterestType.DAILY_PER_MILLION)
                                        .interestRate(dailyInterestRate)
                                        .storageFeeRate(dailyStorageFeeRate)
                                        .build();

                        LocalDate startDate = LocalDate.of(2026, 3, 1);
                        LocalDate endDate = startDate.plusDays(17);

                        // factor = 15.35000000
                        // interest = 15.35 * 1500 * 17 = 391,425.00 VND
                        // storage = 15.35 * 500 * 17 = 130,475.00 VND
                        // totalDue = 521,900.00 VND
                        InterestCalculationResponse res = contractService.calculateInterest(contract, startDate,
                                        endDate);
                        assertEquals(17, res.getDaysCount());
                        assertEquals(new BigDecimal("391425.00"), res.getInterestAmount());
                        assertEquals(new BigDecimal("130475.00"), res.getStorageFeeAmount());
                        assertEquals(new BigDecimal("521900.00"), res.getTotalDue());
                }

                @Test
                @DisplayName("PERCENT_MONTHLY: Odd loan days (17 days and 43 days) with rounding precision")
                void testPercentMonthlyOddDaysExactMath() {
                        BigDecimal principal = new BigDecimal("10000000.00"); // 10M VND
                        BigDecimal monthlyRate = new BigDecimal("3.00"); // 3% / month
                        BigDecimal monthlyStorageRate = new BigDecimal("1.00"); // 1% / month

                        ContractEntity contract = ContractEntity.builder()
                                        .principalAmount(principal)
                                        .remainingPrincipal(principal)
                                        .interestType(InterestType.PERCENT_MONTHLY)
                                        .interestRate(monthlyRate)
                                        .storageFeeRate(monthlyStorageRate)
                                        .build();

                        LocalDate startDate = LocalDate.of(2026, 5, 1);

                        // Case A: 17 days
                        // rateFactor = 0.03000000
                        // dayFactor = 17 / 30 = 0.56666667
                        // interest = 10,000,000 * 0.03 * 0.56666667 = 170000.00 VND
                        // storage = 10,000,000 * 0.01 * 0.56666667 = 56666.67 VND
                        // totalDue = 170000.00 + 56666.67 = 226666.67 VND
                        LocalDate date17Days = startDate.plusDays(17);
                        InterestCalculationResponse res17 = contractService.calculateInterest(contract, startDate,
                                        date17Days);
                        assertEquals(17, res17.getDaysCount());
                        assertEquals(new BigDecimal("170000.00"), res17.getInterestAmount());
                        assertEquals(new BigDecimal("56666.67"), res17.getStorageFeeAmount());
                        assertEquals(new BigDecimal("226666.67"), res17.getTotalDue());

                        // Case B: 43 days
                        // dayFactor = 43 / 30 = 1.43333333
                        // interest = 10,000,000 * 0.03 * 1.43333333 = 430000.00 VND
                        // storage = 10,000,000 * 0.01 * 1.43333333 = 143333.33 VND
                        // totalDue = 573333.33 VND
                        LocalDate date43Days = startDate.plusDays(43);
                        InterestCalculationResponse res43 = contractService.calculateInterest(contract, startDate,
                                        date43Days);
                        assertEquals(43, res43.getDaysCount());
                        assertEquals(new BigDecimal("430000.00"), res43.getInterestAmount());
                        assertEquals(new BigDecimal("143333.33"), res43.getStorageFeeAmount());
                        assertEquals(new BigDecimal("573333.33"), res43.getTotalDue());

                        // Case C: Exactly 30 days (1 full month)
                        // interest = 10,000,000 * 0.03 * 1.0 = 300,000.00 VND
                        // storage = 10,000,000 * 0.01 * 1.0 = 100,000.00 VND
                        // totalDue = 400,000.00 VND
                        LocalDate date30Days = startDate.plusDays(30);
                        InterestCalculationResponse res30 = contractService.calculateInterest(contract, startDate,
                                        date30Days);
                        assertEquals(30, res30.getDaysCount());
                        assertEquals(new BigDecimal("300000.00"), res30.getInterestAmount());
                        assertEquals(new BigDecimal("100000.00"), res30.getStorageFeeAmount());
                        assertEquals(new BigDecimal("400000.00"), res30.getTotalDue());
                }

                @Test
                @DisplayName("PERCENT_WEEKLY: Odd loan days (17 days and 43 days) with weekly divisor 7")
                void testPercentWeeklyOddDaysExactMath() {
                        BigDecimal principal = new BigDecimal("7000000.00"); // 7M VND
                        BigDecimal weeklyRate = new BigDecimal("2.00"); // 2% / week
                        BigDecimal weeklyStorageRate = new BigDecimal("0.50"); // 0.5% / week

                        ContractEntity contract = ContractEntity.builder()
                                        .principalAmount(principal)
                                        .remainingPrincipal(principal)
                                        .interestType(InterestType.PERCENT_WEEKLY)
                                        .interestRate(weeklyRate)
                                        .storageFeeRate(weeklyStorageRate)
                                        .build();

                        LocalDate startDate = LocalDate.of(2026, 7, 1);

                        // Case A: 17 days
                        // dayFactor = 17 / 7 = 2.42857143
                        // interest = 7,000,000 * 0.02 * 2.42857143 = 340,000.00 VND
                        // storage = 7,000,000 * 0.005 * 2.42857143 = 85,000.00 VND
                        // totalDue = 425,000.00 VND
                        LocalDate date17Days = startDate.plusDays(17);
                        InterestCalculationResponse res17 = contractService.calculateInterest(contract, startDate,
                                        date17Days);
                        assertEquals(17, res17.getDaysCount());
                        assertEquals(new BigDecimal("340000.00"), res17.getInterestAmount());
                        assertEquals(new BigDecimal("85000.00"), res17.getStorageFeeAmount());
                        assertEquals(new BigDecimal("425000.00"), res17.getTotalDue());

                        // Case B: 43 days
                        // dayFactor = 43 / 7 = 6.14285714
                        // interest = 7,000,000 * 0.02 * 6.14285714 = 860,000.00 VND
                        // storage = 7,000,000 * 0.005 * 6.14285714 = 215,000.00 VND
                        // totalDue = 1,075,000.00 VND
                        LocalDate date43Days = startDate.plusDays(43);
                        InterestCalculationResponse res43 = contractService.calculateInterest(contract, startDate,
                                        date43Days);
                        assertEquals(43, res43.getDaysCount());
                        assertEquals(new BigDecimal("860000.00"), res43.getInterestAmount());
                        assertEquals(new BigDecimal("215000.00"), res43.getStorageFeeAmount());
                        assertEquals(new BigDecimal("1075000.00"), res43.getTotalDue());

                        // Case C: Exactly 7 days (1 full week)
                        // interest = 7,000,000 * 0.02 * 1.0 = 140,000.00 VND
                        // storage = 7,000,000 * 0.005 * 1.0 = 35,000.00 VND
                        // totalDue = 175,000.00 VND
                        LocalDate date7Days = startDate.plusDays(7);
                        InterestCalculationResponse res7 = contractService.calculateInterest(contract, startDate,
                                        date7Days);
                        assertEquals(7, res7.getDaysCount());
                        assertEquals(new BigDecimal("140000.00"), res7.getInterestAmount());
                        assertEquals(new BigDecimal("35000.00"), res7.getStorageFeeAmount());
                        assertEquals(new BigDecimal("175000.00"), res7.getTotalDue());
                }

                @Test
                @DisplayName("Zero loan days: fromDate == toDate -> interest, storage, and totalDue are 0.00")
                void testZeroLoanDaysReturnsZeroCharges() {
                        BigDecimal principal = new BigDecimal("50000000.00");
                        ContractEntity contract = ContractEntity.builder()
                                        .principalAmount(principal)
                                        .remainingPrincipal(principal)
                                        .interestType(InterestType.DAILY_PER_MILLION)
                                        .interestRate(new BigDecimal("3000.00"))
                                        .storageFeeRate(new BigDecimal("1000.00"))
                                        .build();

                        LocalDate today = LocalDate.now();
                        InterestCalculationResponse res = contractService.calculateInterest(contract, today, today);

                        assertEquals(0, res.getDaysCount());
                        assertEquals(0, BigDecimal.ZERO.compareTo(res.getInterestAmount()));
                        assertEquals(0, BigDecimal.ZERO.compareTo(res.getStorageFeeAmount()));
                        assertEquals(0, BigDecimal.ZERO.compareTo(res.getTotalDue()));
                }

                @Test
                @DisplayName("Negative date range: toDate is before fromDate -> throws INVALID_REQUEST")
                void testNegativeDateRangeThrowsInvalidRequest() {
                        ContractEntity contract = ContractEntity.builder()
                                        .principalAmount(new BigDecimal("10000000.00"))
                                        .remainingPrincipal(new BigDecimal("10000000.00"))
                                        .build();

                        LocalDate from = LocalDate.of(2026, 6, 10);
                        LocalDate to = LocalDate.of(2026, 6, 5); // 5 days in reverse

                        BusinessException ex = assertThrows(BusinessException.class,
                                        () -> contractService.calculateInterest(contract, from, to));

                        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
                }

                @Test
                @DisplayName("Null parameters to calculateInterest throws INVALID_REQUEST")
                void testNullParametersToCalculateInterestThrowsInvalidRequest() {
                        LocalDate date = LocalDate.now();
                        ContractEntity contract = ContractEntity.builder().build();

                        assertThrows(BusinessException.class,
                                        () -> contractService.calculateInterest(null, date, date));
                        assertThrows(BusinessException.class,
                                        () -> contractService.calculateInterest(contract, null, date));
                        assertThrows(BusinessException.class,
                                        () -> contractService.calculateInterest(contract, date, null));
                }
        }

        // =========================================================================
        // SECTION 4: SERVICING, PAYDOWNS & REDEMPTION
        // =========================================================================

        @Nested
        @DisplayName("4. Contract Servicing, Partial Paydowns & Redemption Settlement")
        class ContractServicingAndSettlementEmpiricalTests {

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

                private BranchEntity branch;
                private ContractEntity activeContract;
                private CollateralEntity collateral;

                @BeforeEach
                void setUp() {
                        branch = BranchEntity.builder()
                                        .branchCode("CN-01")
                                        .branchName("Chi nhánh 1")
                                        .currentCashBalance(new BigDecimal("500000000.00"))
                                        .build();
                        branch.setId(1L);

                        collateral = CollateralEntity.builder()
                                        .collateralCode("TS-VAULT-01")
                                        .status(CollateralStatus.IN_VAULT)
                                        .build();
                        collateral.setId(20L);

                        activeContract = ContractEntity.builder()
                                        .contractCode("HD-ACTIVE-01")
                                        .branch(branch)
                                        .collateral(collateral)
                                        .principalAmount(new BigDecimal("20000000.00"))
                                        .remainingPrincipal(new BigDecimal("20000000.00"))
                                        .status(ContractStatus.ACTIVE)
                                        .paymentCycleDays(10)
                                        .nextInterestDueDate(LocalDate.of(2026, 11, 20))
                                        .totalInterestPaid(BigDecimal.ZERO)
                                        .build();
                        activeContract.setId(701L);

                        lenient().when(contractRepository.findByIdAndDeletedFalse(701L))
                                        .thenReturn(Optional.of(activeContract));
                        lenient().when(contractRepository.save(any(ContractEntity.class)))
                                        .thenAnswer(i -> i.getArgument(0));
                        lenient().when(collateralRepository.save(any(CollateralEntity.class)))
                                        .thenAnswer(i -> i.getArgument(0));
                        lenient().when(interestPaymentRepository.save(any(InterestPaymentEntity.class)))
                                        .thenAnswer(i -> i.getArgument(0));
                }

                @Test
                @DisplayName("Partial paydown exact subtraction: 20M principal reduced by 5M -> remaining is exactly 15M")
                void testPartialPaydownExactSubtraction() {
                        InterestCollectionRequest req = InterestCollectionRequest.builder()
                                        .interestAmount(new BigDecimal("600000.00"))
                                        .storageFeeAmount(new BigDecimal("200000.00"))
                                        .penaltyAmount(BigDecimal.ZERO)
                                        .principalPaidAmount(new BigDecimal("5000000.00"))
                                        .paymentMethod("CASH")
                                        .notes("Đóng lãi và trả bớt gốc 5M")
                                        .build();

                        InterestPaymentResponse resp = contractService.collectInterest(701L, req, 1L);

                        assertNotNull(resp);
                        assertEquals(0, new BigDecimal("15000000.00").compareTo(activeContract.getRemainingPrincipal()),
                                        "Dư nợ gốc sau khi trả 5M trên 20M phải còn chính xác 15M");
                        assertEquals(0, new BigDecimal("800000.00").compareTo(activeContract.getTotalInterestPaid()));
                        assertEquals(LocalDate.of(2026, 11, 30), activeContract.getNextInterestDueDate());

                        // CASH_IN voucher for total payment (600k + 200k + 5M = 5.8M)
                        verify(cashbookService).collectCashIn(
                                        eq(1L),
                                        eq(new BigDecimal("5800000.00")),
                                        eq(TransactionCategory.INTEREST_COLLECTION),
                                        eq(701L),
                                        eq(1L),
                                        any());
                }

                @Test
                @DisplayName("Partial paydown exact full principal: 20M paid on 20M -> remainingPrincipal reaches exactly 0")
                void testPartialPaydownReachesExactlyZeroPrincipal() {
                        InterestCollectionRequest req = InterestCollectionRequest.builder()
                                        .interestAmount(BigDecimal.ZERO)
                                        .storageFeeAmount(BigDecimal.ZERO)
                                        .principalPaidAmount(new BigDecimal("20000000.00"))
                                        .build();

                        InterestPaymentResponse resp = contractService.collectInterest(701L, req, 1L);

                        assertNotNull(resp);
                        assertEquals(0, BigDecimal.ZERO.compareTo(activeContract.getRemainingPrincipal()),
                                        "Dư nợ gốc phải về đúng 0");
                }

                @Test
                @DisplayName("Over-paydown attempt: paid amount exceeds remainingPrincipal -> throws INVALID_REQUEST")
                void testOverPaydownExceedingRemainingPrincipalThrowsInvalidRequest() {
                        InterestCollectionRequest req = InterestCollectionRequest.builder()
                                        .principalPaidAmount(new BigDecimal("20000001.00")) // Exceeds by 1 VND
                                        .build();

                        BusinessException ex = assertThrows(BusinessException.class,
                                        () -> contractService.collectInterest(701L, req, 1L));

                        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
                        assertEquals(0, new BigDecimal("20000000.00").compareTo(activeContract.getRemainingPrincipal()),
                                        "Dư nợ gốc không được thay đổi khi giao dịch bị từ chối");
                        verify(cashbookService, never()).collectCashIn(any(), any(), any(TransactionCategory.class),
                                        any(), any(), any());
                }

                @Test
                @DisplayName("Negative paydown amount attempt -> throws INVALID_REQUEST")
                void testNegativePaydownAmountThrowsInvalidRequest() {
                        InterestCollectionRequest req = InterestCollectionRequest.builder()
                                        .principalPaidAmount(new BigDecimal("-1000000.00"))
                                        .build();

                        BusinessException ex = assertThrows(BusinessException.class,
                                        () -> contractService.collectInterest(701L, req, 1L));

                        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
                }

                @Test
                @DisplayName("Full redemption settlement: remainingPrincipal reaches exactly 0 and collateral is REDEEMED")
                void testFullRedemptionSettlement() {
                        ContractResponse resp = contractService.redeemContract(701L, 1L,
                                        "Khách hàng tất toán chuộc tài sản");

                        assertNotNull(resp);
                        assertEquals(ContractStatus.REDEEMED, resp.getStatus());
                        assertEquals(ContractStatus.REDEEMED, activeContract.getStatus());
                        assertEquals(0, BigDecimal.ZERO.compareTo(activeContract.getRemainingPrincipal()),
                                        "Dư nợ gốc sau khi tất toán chuộc đồ phải về đúng 0");
                        assertEquals(CollateralStatus.REDEEMED, collateral.getStatus(),
                                        "Tài sản phải được chuyển trạng thái REDEEMED để xuất kho két");
                        assertNotNull(activeContract.getClosedDate());

                        // CASH_IN voucher for exact remaining principal 20M
                        verify(cashbookService).collectCashIn(
                                        eq(1L),
                                        eq(new BigDecimal("20000000.00")),
                                        eq(TransactionCategory.PRINCIPAL_COLLECTION),
                                        eq(701L),
                                        eq(1L),
                                        eq("Khách hàng tất toán chuộc tài sản"));
                }

                @ParameterizedTest(name = "Redemption on already closed contract with status {0} throws CONTRACT_ALREADY_CLOSED")
                @EnumSource(value = ContractStatus.class, names = { "REDEEMED", "LIQUIDATED", "CANCELLED" })
                void testRedemptionOnAlreadyClosedContractThrowsContractAlreadyClosed(ContractStatus closedStatus) {
                        activeContract.setStatus(closedStatus);

                        BusinessException ex = assertThrows(BusinessException.class,
                                        () -> contractService.redeemContract(701L, 1L, "Chuộc lại hợp đồng đã đóng"));

                        assertEquals(ErrorCode.CONTRACT_ALREADY_CLOSED, ex.getErrorCode());
                        verify(cashbookService, never()).collectCashIn(any(), any(), any(TransactionCategory.class),
                                        any(), any(), any());
                }

                @Test
                @DisplayName("Liquidation of active contract: status LIQUIDATED, collateral LIQUIDATED, proceeds recorded in cashbook")
                void testLiquidationSuccess() {
                        LiquidationRequest req = LiquidationRequest.builder()
                                        .liquidationAmount(new BigDecimal("22500000.00"))
                                        .notes("Đã bán đấu giá tài sản cầm cố thành công")
                                        .build();

                        ContractResponse resp = contractService.liquidateContract(701L, req, 1L);

                        assertNotNull(resp);
                        assertEquals(ContractStatus.LIQUIDATED, resp.getStatus());
                        assertEquals(0, BigDecimal.ZERO.compareTo(activeContract.getRemainingPrincipal()));
                        assertEquals(CollateralStatus.LIQUIDATED, collateral.getStatus());
                        assertNotNull(activeContract.getClosedDate());

                        verify(cashbookService).collectCashIn(
                                        eq(1L),
                                        eq(new BigDecimal("22500000.00")),
                                        eq(TransactionCategory.LIQUIDATION_INCOME),
                                        eq(701L),
                                        eq(1L),
                                        contains("Đã bán đấu giá tài sản"));
                }

                @ParameterizedTest(name = "Liquidation with invalid amount {0} throws INVALID_REQUEST")
                @ValueSource(strings = { "0", "-1", "-10000000" })
                void testLiquidationWithZeroOrNegativeAmountThrowsInvalidRequest(String invalidAmountStr) {
                        LiquidationRequest req = LiquidationRequest.builder()
                                        .liquidationAmount(new BigDecimal(invalidAmountStr))
                                        .notes("Số tiền thanh lý sai")
                                        .build();

                        BusinessException ex = assertThrows(BusinessException.class,
                                        () -> contractService.liquidateContract(701L, req, 1L));

                        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
                        assertEquals(ContractStatus.ACTIVE, activeContract.getStatus());
                }

                @Test
                @DisplayName("Overdue scanning transitions expired contracts to OVERDUE and collateral to WAITING_LIQUIDATION")
                void testOverdueScanningLogic() {
                        LocalDate asOfDate = LocalDate.of(2026, 12, 1);

                        ContractEntity expiredContract = ContractEntity.builder()
                                        .contractCode("HD-EXPIRED-01")
                                        .status(ContractStatus.ACTIVE)
                                        .maturityDate(LocalDate.of(2026, 11, 25)) // expired 6 days ago
                                        .collateral(collateral)
                                        .build();

                        ContractEntity currentContract = ContractEntity.builder()
                                        .contractCode("HD-CURRENT-01")
                                        .status(ContractStatus.ACTIVE)
                                        .maturityDate(LocalDate.of(2026, 12, 10)) // expires in future
                                        .collateral(CollateralEntity.builder().status(CollateralStatus.IN_VAULT)
                                                        .build())
                                        .build();

                        when(contractRepository.findByStatusAndDeletedFalse(ContractStatus.ACTIVE))
                                        .thenReturn(List.of(expiredContract, currentContract));

                        int overdueCount = contractService.markOverdueContracts(asOfDate);

                        assertEquals(1, overdueCount);
                        assertEquals(ContractStatus.OVERDUE, expiredContract.getStatus());
                        assertEquals(CollateralStatus.WAITING_LIQUIDATION, collateral.getStatus());

                        assertEquals(ContractStatus.ACTIVE, currentContract.getStatus());
                        assertEquals(CollateralStatus.IN_VAULT, currentContract.getCollateral().getStatus());
                }
        }
}

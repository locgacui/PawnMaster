package com.pawnmaster.api.modules.cashbook;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.branch.BranchRepository;
import com.pawnmaster.api.modules.cashbook.dto.CashbookResponse;
import com.pawnmaster.api.modules.cashbook.dto.CashFlowSummaryResponse;
import com.pawnmaster.api.modules.contract.ContractEntity;
import com.pawnmaster.api.modules.contract.ContractRepository;
import com.pawnmaster.api.modules.customer.CustomerEntity;
import com.pawnmaster.api.modules.user.UserEntity;
import com.pawnmaster.api.modules.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Checkpoint 3: CashbookService Unit & Domain Tests")
public class CashbookServiceTest {

        @Mock
        private CashbookRepository cashbookRepository;

        @Mock
        private BranchRepository branchRepository;

        @Mock
        private UserRepository userRepository;

        @Mock
        private ContractRepository contractRepository;

        @InjectMocks
        private CashbookServiceImpl cashbookService;

        private BranchEntity sampleBranch;
        private UserEntity sampleStaff;
        private ContractEntity sampleContract;
        private CustomerEntity sampleCustomer;

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

                sampleStaff = UserEntity.builder()
                                .username("staff01")
                                .fullName("Nguyễn Văn Nhân Viên")
                                .branch(sampleBranch)
                                .build();
                sampleStaff.setId(10L);

                sampleCustomer = CustomerEntity.builder()
                                .customerCode("KH-001")
                                .fullName("Trần Thị Khách")
                                .branch(sampleBranch)
                                .build();
                sampleCustomer.setId(100L);

                sampleContract = ContractEntity.builder()
                                .contractCode("HD-2026-0001")
                                .customer(sampleCustomer)
                                .branch(sampleBranch)
                                .principalAmount(new BigDecimal("20000000.00"))
                                .remainingPrincipal(new BigDecimal("20000000.00"))
                                .build();
                sampleContract.setId(200L);
        }

        // =========================================================================
        // 1. CASH IN (PHIẾU THU) TESTS
        // =========================================================================

        @Test
        @DisplayName("collectCashIn - Thành công tăng số dư quỹ chi nhánh và ghi nhận phiếu thu")
        void collectCashIn_IncrementsBranchBalance_AndRecordsEntry() {
                when(branchRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(sampleBranch));
                when(cashbookRepository.save(any(CashbookEntity.class))).thenAnswer(invocation -> {
                        CashbookEntity entity = invocation.getArgument(0);
                        entity.setId(1001L);
                        return entity;
                });

                BigDecimal cashInAmount = new BigDecimal("10000000.00");
                BigDecimal expectedBalanceAfter = new BigDecimal("510000000.00"); // 500M + 10M

                CashbookResponse response = cashbookService.collectCashIn(
                                1L,
                                cashInAmount,
                                TransactionCategory.INTEREST_COLLECTION,
                                200L,
                                10L,
                                "Thu lãi kỳ 1 hợp đồng HD-2026-0001");

                assertNotNull(response);
                assertEquals(TransactionType.CASH_IN, response.getTransactionType());
                assertEquals(TransactionCategory.INTEREST_COLLECTION, response.getCategory());
                assertEquals(cashInAmount, response.getAmount());
                assertEquals(expectedBalanceAfter, response.getBalanceAfter());
                assertEquals(expectedBalanceAfter, sampleBranch.getCurrentCashBalance());

                // Verify Branch balance updated & saved
                verify(branchRepository, times(1)).save(sampleBranch);

                // Verify Cashbook voucher saved with correct attributes
                ArgumentCaptor<CashbookEntity> captor = ArgumentCaptor.forClass(CashbookEntity.class);
                verify(cashbookRepository, times(1)).save(captor.capture());
                CashbookEntity saved = captor.getValue();
                assertEquals(TransactionType.CASH_IN, saved.getTransactionType());
                assertEquals(TransactionCategory.INTEREST_COLLECTION, saved.getCategory());
                assertEquals(cashInAmount, saved.getAmount());
                assertEquals(expectedBalanceAfter, saved.getBalanceAfter());
                assertEquals(sampleBranch, saved.getBranch());
        }

        @Test
        @DisplayName("collectCashIn - Thành công cho các loại phiếu thu khác nhau")
        void collectCashIn_SupportsMultipleCategories() {
                when(branchRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(sampleBranch));
                when(cashbookRepository.save(any(CashbookEntity.class))).thenAnswer(inv -> inv.getArgument(0));

                // 1. PRINCIPAL_COLLECTION (Thu gốc)
                CashbookResponse r1 = cashbookService.collectCashIn(1L, new BigDecimal("5000000.00"),
                                TransactionCategory.PRINCIPAL_COLLECTION, 200L, 10L, "Trả bớt gốc");
                assertEquals(TransactionCategory.PRINCIPAL_COLLECTION, r1.getCategory());
                assertEquals(new BigDecimal("505000000.00"), sampleBranch.getCurrentCashBalance());

                // 2. LIQUIDATION_INCOME (Thu thanh lý)
                CashbookResponse r2 = cashbookService.collectCashIn(1L, new BigDecimal("12000000.00"),
                                TransactionCategory.LIQUIDATION_INCOME, 200L, 10L, "Thanh lý tài sản");
                assertEquals(TransactionCategory.LIQUIDATION_INCOME, r2.getCategory());
                assertEquals(new BigDecimal("517000000.00"), sampleBranch.getCurrentCashBalance());
        }

        @Test
        @DisplayName("collectCashIn - Ném BRANCH_NOT_FOUND khi chi nhánh không tồn tại")
        void collectCashIn_BranchNotFound_ThrowsException() {
                when(branchRepository.findByIdAndDeletedFalse(999L)).thenReturn(Optional.empty());

                BusinessException ex = assertThrows(BusinessException.class,
                                () -> cashbookService.collectCashIn(999L, new BigDecimal("1000000.00"),
                                                TransactionCategory.INTEREST_COLLECTION, null, 10L, "Lỗi"));

                assertEquals(ErrorCode.BRANCH_NOT_FOUND, ex.getErrorCode());
                verify(cashbookRepository, never()).save(any());
        }

        @Test
        @DisplayName("collectCashIn - Ném INVALID_REQUEST khi số tiền thu <= 0")
        void collectCashIn_ZeroOrNegativeAmount_ThrowsInvalidRequest() {
                BusinessException ex1 = assertThrows(BusinessException.class, () -> cashbookService.collectCashIn(1L,
                                BigDecimal.ZERO, TransactionCategory.INTEREST_COLLECTION, null, 10L, ""));
                assertEquals(ErrorCode.INVALID_REQUEST, ex1.getErrorCode());

                BusinessException ex2 = assertThrows(BusinessException.class, () -> cashbookService.collectCashIn(1L,
                                new BigDecimal("-100000"), TransactionCategory.INTEREST_COLLECTION, null, 10L, ""));
                assertEquals(ErrorCode.INVALID_REQUEST, ex2.getErrorCode());
        }

        // =========================================================================
        // 2. DISBURSEMENT / CASH OUT (PHIẾU CHI) TESTS
        // =========================================================================

        @Test
        @DisplayName("disburseLoan - Thành công giảm số dư quỹ chi nhánh và ghi nhận phiếu chi")
        void disburseLoan_DecrementsBranchBalance_AndRecordsEntry() {
                when(branchRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(sampleBranch));
                when(cashbookRepository.save(any(CashbookEntity.class))).thenAnswer(invocation -> {
                        CashbookEntity entity = invocation.getArgument(0);
                        entity.setId(1002L);
                        return entity;
                });

                BigDecimal disburseAmount = new BigDecimal("20000000.00");
                BigDecimal expectedBalanceAfter = new BigDecimal("480000000.00"); // 500M - 20M

                CashbookResponse response = cashbookService.disburseLoan(
                                1L,
                                disburseAmount,
                                200L,
                                10L,
                                "Chi giải ngân hợp đồng HD-2026-0001");

                assertNotNull(response);
                assertEquals(TransactionType.CASH_OUT, response.getTransactionType());
                assertEquals(TransactionCategory.DISBURSEMENT, response.getCategory());
                assertEquals(disburseAmount, response.getAmount());
                assertEquals(expectedBalanceAfter, response.getBalanceAfter());
                assertEquals(expectedBalanceAfter, sampleBranch.getCurrentCashBalance());

                verify(branchRepository, times(1)).save(sampleBranch);

                ArgumentCaptor<CashbookEntity> captor = ArgumentCaptor.forClass(CashbookEntity.class);
                verify(cashbookRepository, times(1)).save(captor.capture());
                CashbookEntity saved = captor.getValue();
                assertEquals(TransactionType.CASH_OUT, saved.getTransactionType());
                assertEquals(TransactionCategory.DISBURSEMENT, saved.getCategory());
                assertEquals(disburseAmount, saved.getAmount());
                assertEquals(expectedBalanceAfter, saved.getBalanceAfter());
                assertEquals(sampleBranch, saved.getBranch());
        }

        @Test
        @DisplayName("disburseLoan - Ném INSUFFICIENT_CASH_BALANCE khi số tiền giải ngân > số dư quỹ chi nhánh")
        void disburseLoan_AmountExceedsBalance_ThrowsInsufficientCashBalance() {
                sampleBranch.setCurrentCashBalance(new BigDecimal("10000000.00")); // Quỹ chỉ còn 10M
                when(branchRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(sampleBranch));

                BigDecimal disburseAmount = new BigDecimal("15000000.00"); // Đòi chi 15M

                BusinessException ex = assertThrows(BusinessException.class,
                                () -> cashbookService.disburseLoan(1L, disburseAmount, 200L, 10L, "Giải ngân"));

                assertEquals(ErrorCode.INSUFFICIENT_CASH_BALANCE, ex.getErrorCode());
                // Số dư không bị thay đổi
                assertEquals(new BigDecimal("10000000.00"), sampleBranch.getCurrentCashBalance());
                verify(branchRepository, never()).save(any());
                verify(cashbookRepository, never()).save(any());
        }

        @Test
        @DisplayName("disburseLoan - Thành công khi số tiền giải ngân bằng chính xác số dư quỹ (về 0)")
        void disburseLoan_ExactBalance_DecrementsToZero() {
                sampleBranch.setCurrentCashBalance(new BigDecimal("20000000.00"));
                when(branchRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(sampleBranch));
                when(cashbookRepository.save(any(CashbookEntity.class))).thenAnswer(inv -> inv.getArgument(0));

                CashbookResponse response = cashbookService.disburseLoan(1L, new BigDecimal("20000000.00"), 200L, 10L,
                                "Chi hết quỹ");

                assertNotNull(response);
                assertEquals(BigDecimal.ZERO.setScale(2), response.getBalanceAfter().setScale(2));
                assertEquals(BigDecimal.ZERO.setScale(2), sampleBranch.getCurrentCashBalance().setScale(2));
                verify(branchRepository, times(1)).save(sampleBranch);
        }

        // =========================================================================
        // 3. TRANSACTION HISTORY & CASH FLOW SUMMARY
        // =========================================================================

        @Test
        @DisplayName("getTransactionsByBranch - Trả về lịch sử giao dịch sổ quỹ theo chi nhánh")
        void getTransactionsByBranch_ReturnsList() {
                CashbookEntity e1 = CashbookEntity.builder()
                                .transactionCode("CB-01")
                                .branch(sampleBranch)
                                .transactionType(TransactionType.CASH_IN)
                                .category(TransactionCategory.INTEREST_COLLECTION)
                                .amount(new BigDecimal("1000000.00"))
                                .balanceAfter(new BigDecimal("501000000.00"))
                                .transactionDate(LocalDateTime.now())
                                .build();
                e1.setId(1L);

                when(cashbookRepository.findByBranchIdAndDeletedFalseOrderByTransactionDateDesc(1L))
                                .thenReturn(List.of(e1));

                List<CashbookResponse> list = cashbookService.getTransactionsByBranch(1L);

                assertEquals(1, list.size());
                assertEquals("CB-01", list.get(0).getTransactionCode());
        }

        @Test
        @DisplayName("getBranchCashFlowSummary - Tính toán chính xác tổng thu, tổng chi và dòng tiền thuần")
        void getBranchCashFlowSummary_CalculatesTotalsCorrectly() {
                CashbookEntity in1 = CashbookEntity.builder()
                                .transactionType(TransactionType.CASH_IN)
                                .amount(new BigDecimal("10000000.00"))
                                .build();
                CashbookEntity in2 = CashbookEntity.builder()
                                .transactionType(TransactionType.CASH_IN)
                                .amount(new BigDecimal("5000000.00"))
                                .build();
                CashbookEntity out1 = CashbookEntity.builder()
                                .transactionType(TransactionType.CASH_OUT)
                                .amount(new BigDecimal("8000000.00"))
                                .build();

                when(branchRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(sampleBranch));
                when(cashbookRepository.findByBranchIdAndDeletedFalseOrderByTransactionDateDesc(1L))
                                .thenReturn(List.of(in1, in2, out1));

                CashFlowSummaryResponse summary = cashbookService.getBranchCashFlowSummary(1L);

                assertNotNull(summary);
                assertEquals(new BigDecimal("15000000.00"), summary.getTotalCashIn());
                assertEquals(new BigDecimal("8000000.00"), summary.getTotalCashOut());
                assertEquals(new BigDecimal("7000000.00"), summary.getNetCashFlow()); // 15M - 8M = 7M
                assertEquals(new BigDecimal("500000000.00"), summary.getCurrentCashBalance());
        }
}

package com.pawnmaster.api.modules.contract;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.branch.BranchRepository;
import com.pawnmaster.api.modules.cashbook.CashbookService;
import com.pawnmaster.api.modules.cashbook.dto.CashbookResponse;
import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.collateral.CollateralEntity;
import com.pawnmaster.api.modules.collateral.CollateralRepository;
import com.pawnmaster.api.modules.collateral.CollateralStatus;
import com.pawnmaster.api.modules.collateral.LtvPolicyService;
import com.pawnmaster.api.modules.customer.VerificationDocumentPolicy;
import com.pawnmaster.api.modules.contract.dto.ContractCreateRequest;
import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import com.pawnmaster.api.modules.customer.CustomerEntity;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.CustomerRepository;
import com.pawnmaster.api.modules.customer.VerificationDocumentType;
import com.pawnmaster.api.modules.user.RoleEnum;
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
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Checkpoint 3: Contract Maker-Checker Approval Workflow Tests")
public class ContractApprovalWorkflowTest {

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

        @InjectMocks
        private ContractServiceImpl contractService;

        private BranchEntity branch1;
        private BranchEntity branch2;
        private UserEntity staffBranch1;
        private UserEntity managerBranch1;
        private UserEntity managerBranch2;
        private UserEntity adminUser;

        private CustomerEntity customer;
        private CollateralEntity collateral;
        private ContractCreateRequest createRequest;
        private ContractEntity samplePendingContract;

        @BeforeEach
        void setUp() {
                branch1 = BranchEntity.builder()
                                .branchCode("CN-HCM-01")
                                .branchName("Chi nhánh Quận 1")
                                .provinceCode("79")
                                .districtCode("760")
                                .currentCashBalance(new BigDecimal("500000000.00"))
                                .build();
                branch1.setId(1L);

                branch2 = BranchEntity.builder()
                                .branchCode("CN-HN-01")
                                .branchName("Chi nhánh Cầu Giấy")
                                .provinceCode("01")
                                .districtCode("005")
                                .currentCashBalance(new BigDecimal("300000000.00"))
                                .build();
                branch2.setId(2L);

                staffBranch1 = UserEntity.builder()
                                .username("staff_q1")
                                .fullName("Nhân viên Q1")
                                .role(RoleEnum.STAFF)
                                .branch(branch1)
                                .build();
                staffBranch1.setId(10L);

                managerBranch1 = UserEntity.builder()
                                .username("manager_q1")
                                .fullName("Quản lý Q1")
                                .role(RoleEnum.MANAGER)
                                .branch(branch1)
                                .build();
                managerBranch1.setId(11L);

                managerBranch2 = UserEntity.builder()
                                .username("manager_hn")
                                .fullName("Quản lý HN")
                                .role(RoleEnum.MANAGER)
                                .branch(branch2)
                                .build();
                managerBranch2.setId(12L);

                adminUser = UserEntity.builder()
                                .username("superadmin")
                                .fullName("Tổng Quản Trị")
                                .role(RoleEnum.ADMIN)
                                .branch(null)
                                .build();
                adminUser.setId(1L);

                customer = CustomerEntity.builder()
                                .customerCode("KH-001")
                                .fullName("Phạm Văn Khách")
                                .occupationGroup(CustomerOccupationGroup.OFFICE_WORKER)
                                .verificationDocType(VerificationDocumentType.PAYSLIP_BANK_STATEMENT)
                                .verificationDocUrls("https://storage.pawnmaster.vn/docs/payslip.jpg")
                                .build();
                customer.setId(100L);

                collateral = CollateralEntity.builder()
                                .collateralCode("TS-001")
                                .customer(customer)
                                .branch(branch1)
                                .name("Honda SH 150i")
                                .category(CollateralCategory.MOTORBIKE)
                                .appraisedValue(new BigDecimal("50000000.00"))
                                .maxLtvPercent(new BigDecimal("0.90"))
                                .status(CollateralStatus.IN_VAULT)
                                .build();
                collateral.setId(200L);

                createRequest = ContractCreateRequest.builder()
                                .customerId(100L)
                                .collateralId(200L)
                                .branchId(1L)
                                .principalAmount(new BigDecimal("30000000.00"))
                                .interestType(InterestType.DAILY_PER_MILLION)
                                .interestRate(new BigDecimal("2000.00"))
                                .storageFeeRate(new BigDecimal("500.00"))
                                .loanTermDays(30)
                                .paymentCycleDays(10)
                                .startDate(LocalDate.of(2026, 11, 10))
                                .notes("Hợp đồng cầm cố xe máy SH")
                                .build();

                samplePendingContract = ContractEntity.builder()
                                .contractCode("HD-2026-0001")
                                .customer(customer)
                                .collateral(collateral)
                                .branch(branch1)
                                .principalAmount(new BigDecimal("30000000.00"))
                                .remainingPrincipal(new BigDecimal("30000000.00"))
                                .appliedLtvPercent(new BigDecimal("60.00"))
                                .interestType(InterestType.DAILY_PER_MILLION)
                                .interestRate(new BigDecimal("2000.00"))
                                .storageFeeRate(new BigDecimal("500.00"))
                                .loanTermDays(30)
                                .paymentCycleDays(10)
                                .startDate(LocalDate.of(2026, 11, 10))
                                .maturityDate(LocalDate.of(2026, 12, 10))
                                .nextInterestDueDate(LocalDate.of(2026, 11, 20))
                                .status(ContractStatus.PENDING_APPROVAL)
                                .totalInterestPaid(BigDecimal.ZERO)
                                .build();
                samplePendingContract.setId(500L);

                contractService.setClock(Clock.fixed(
                                LocalDate.of(2026, 11, 10).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant(),
                                ZoneId.systemDefault()));
        }

        // =========================================================================
        // 1. STEP 1 (MAKER): CONTRACT CREATION BY STAFF
        // =========================================================================

        @Test
        @DisplayName("createContract - STAFF tạo hồ sơ: Trạng thái PENDING_APPROVAL, tính đúng kỳ hạn, KHÔNG xuất quỹ")
        void createContract_ByStaff_SetsStatusPendingApproval_CalculatesDates_ZeroCashMovement() {
                when(customerRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(customer));
                when(collateralRepository.findByIdAndDeletedFalse(200L)).thenReturn(Optional.of(collateral));
                when(branchRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(branch1));
                when(contractRepository.save(any(ContractEntity.class))).thenAnswer(inv -> {
                        ContractEntity entity = inv.getArgument(0);
                        entity.setId(500L);
                        return entity;
                });

                ContractResponse response = contractService.createContract(createRequest, staffBranch1.getId());

                assertNotNull(response);
                assertEquals(ContractStatus.PENDING_APPROVAL, response.getStatus());
                assertEquals(new BigDecimal("30000000.00"), response.getPrincipalAmount());
                assertEquals(new BigDecimal("30000000.00"), response.getRemainingPrincipal());
                assertEquals(LocalDate.of(2026, 12, 10), response.getMaturityDate()); // 10/11 + 30 days
                assertEquals(LocalDate.of(2026, 11, 20), response.getNextInterestDueDate()); // 10/11 + 10 days

                // INVARIANT CHECK: Zero cash movement on origination!
                verify(cashbookService, never()).disburseLoan(any(), any(), any(), any(), any());
                verify(branchRepository, never()).save(any());

                // Verify contract persisted with PENDING_APPROVAL
                ArgumentCaptor<ContractEntity> captor = ArgumentCaptor.forClass(ContractEntity.class);
                verify(contractRepository, times(1)).save(captor.capture());
                ContractEntity saved = captor.getValue();
                assertEquals(ContractStatus.PENDING_APPROVAL, saved.getStatus());
                assertNull(saved.getApprovedBy());
                assertNull(saved.getApprovedAt());
        }

        @Test
        @DisplayName("createContract - Thẩm định LTV 2 chiều vượt trần ném EXCEEDS_MAX_LTV")
        void createContract_ExceedsMaxLtv_ThrowsException() {
                when(customerRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(customer));
                when(collateralRepository.findByIdAndDeletedFalse(200L)).thenReturn(Optional.of(collateral));
                when(branchRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(branch1));

                doThrow(new BusinessException(ErrorCode.EXCEEDS_MAX_LTV, "Vượt quá tỷ lệ LTV"))
                                .when(ltvPolicyService).validateLoanCeiling(any(), any(), any(), any());

                assertThrows(BusinessException.class,
                                () -> contractService.createContract(createRequest, staffBranch1.getId()));

                verify(contractRepository, never()).save(any());
                verify(cashbookService, never()).disburseLoan(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("createContract - Thiếu cấu hình LTV trong Database ném BusinessException INVALID_REQUEST")
        void createContract_PolicyNotFoundInDatabase_ThrowsBusinessException() {
                when(customerRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(customer));
                when(collateralRepository.findByIdAndDeletedFalse(200L)).thenReturn(Optional.of(collateral));
                when(branchRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(branch1));

                doThrow(new BusinessException(ErrorCode.INVALID_REQUEST, "Không tìm thấy cấu hình LTV cho nhóm: " + customer.getOccupationGroup() + " và loại: " + collateral.getCategory()))
                                .when(ltvPolicyService).validateLoanCeiling(any(), any(), any(), any());

                BusinessException ex = assertThrows(BusinessException.class,
                                () -> contractService.createContract(createRequest, staffBranch1.getId()));

                assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
                assertTrue(ex.getMessage().contains("Không tìm thấy cấu hình LTV"));
                verify(contractRepository, never()).save(any());
                verify(cashbookService, never()).disburseLoan(any(), any(), any(), any(), any());
        }

        // =========================================================================
        // 2. STEP 2 (CHECKER): APPROVAL & DISBURSEMENT
        // =========================================================================

        @Test
        @DisplayName("approveContract - MANAGER cùng chi nhánh duyệt: Trạng thái ACTIVE, nhập kho IN_VAULT, xuất quỹ CASH_OUT")
        void approveContract_ByManagerOfSameBranch_Success() {
                when(userRepository.findByIdAndDeletedFalse(managerBranch1.getId()))
                                .thenReturn(Optional.of(managerBranch1));
                when(contractRepository.findByIdAndDeletedFalse(500L)).thenReturn(Optional.of(samplePendingContract));
                when(cashbookService.disburseLoan(eq(1L), eq(new BigDecimal("30000000.00")), eq(500L),
                                eq(managerBranch1.getId()), any()))
                                .thenReturn(CashbookResponse.builder().build());

                ContractResponse response = contractService.approveAndDisburseContract(
                                500L,
                                managerBranch1.getId(),
                                "Hồ sơ chứng từ và xe máy kiểm tra hợp lệ, phê duyệt giải ngân");

                assertNotNull(response);
                assertEquals(ContractStatus.ACTIVE, samplePendingContract.getStatus());
                assertEquals(managerBranch1.getId(), samplePendingContract.getApprovedBy());
                assertNotNull(samplePendingContract.getApprovedAt());
                assertEquals("Hồ sơ chứng từ và xe máy kiểm tra hợp lệ, phê duyệt giải ngân",
                                samplePendingContract.getApprovalNotes());
                assertEquals(CollateralStatus.IN_VAULT, collateral.getStatus());

                // Verify Cashbook disbursement was called with branchId 1
                verify(cashbookService, times(1)).disburseLoan(
                                eq(1L),
                                eq(new BigDecimal("30000000.00")),
                                eq(500L),
                                eq(managerBranch1.getId()),
                                anyString());
                verify(contractRepository, times(1)).save(samplePendingContract);
                verify(collateralRepository, times(1)).save(collateral);
        }

        @Test
        @DisplayName("approveContract - ADMIN được phép duyệt hợp đồng của bất kỳ chi nhánh nào")
        void approveContract_ByAdmin_SucceedsForAnyBranch() {
                when(userRepository.findByIdAndDeletedFalse(adminUser.getId())).thenReturn(Optional.of(adminUser));
                when(contractRepository.findByIdAndDeletedFalse(500L)).thenReturn(Optional.of(samplePendingContract));
                when(cashbookService.disburseLoan(any(), any(), any(), any(), any()))
                                .thenReturn(CashbookResponse.builder().build());

                ContractResponse response = contractService.approveAndDisburseContract(500L, adminUser.getId(),
                                "Admin duyệt");

                assertNotNull(response);
                assertEquals(ContractStatus.ACTIVE, samplePendingContract.getStatus());
                verify(cashbookService, times(1)).disburseLoan(eq(1L), any(), any(), eq(adminUser.getId()), any());
        }

        @Test
        @DisplayName("approveContract - STAFF cố tình duyệt ném FORBIDDEN")
        void approveContract_ByStaff_ThrowsForbidden() {
                when(userRepository.findByIdAndDeletedFalse(staffBranch1.getId()))
                                .thenReturn(Optional.of(staffBranch1));
                when(contractRepository.findByIdAndDeletedFalse(500L)).thenReturn(Optional.of(samplePendingContract));

                BusinessException ex = assertThrows(BusinessException.class, () -> contractService
                                .approveAndDisburseContract(500L, staffBranch1.getId(), "Staff cố tình duyệt"));

                assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
                assertEquals(ContractStatus.PENDING_APPROVAL, samplePendingContract.getStatus());
                verify(cashbookService, never()).disburseLoan(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("approveContract - MANAGER chi nhánh khác cố tình duyệt ném FORBIDDEN")
        void approveContract_ByManagerOfDifferentBranch_ThrowsForbidden() {
                // Contract thuộc Branch 1, nhưng approver là Manager của Branch 2
                when(userRepository.findByIdAndDeletedFalse(managerBranch2.getId()))
                                .thenReturn(Optional.of(managerBranch2));
                when(contractRepository.findByIdAndDeletedFalse(500L)).thenReturn(Optional.of(samplePendingContract));

                BusinessException ex = assertThrows(BusinessException.class, () -> contractService
                                .approveAndDisburseContract(500L, managerBranch2.getId(), "Duyệt chéo"));

                assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
                assertEquals(ContractStatus.PENDING_APPROVAL, samplePendingContract.getStatus());
                verify(cashbookService, never()).disburseLoan(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("approveContract - Thất bại và không chuyển ACTIVE khi quỹ chi nhánh không đủ tiền")
        void approveContract_FailsWhenBranchLacksCash() {
                when(userRepository.findByIdAndDeletedFalse(managerBranch1.getId()))
                                .thenReturn(Optional.of(managerBranch1));
                when(contractRepository.findByIdAndDeletedFalse(500L)).thenReturn(Optional.of(samplePendingContract));

                // CashbookService ném INSUFFICIENT_CASH_BALANCE
                doThrow(new BusinessException(ErrorCode.INSUFFICIENT_CASH_BALANCE, "Quỹ chi nhánh không đủ"))
                                .when(cashbookService).disburseLoan(any(), any(), any(), any(), any());

                assertThrows(BusinessException.class, () -> contractService.approveAndDisburseContract(500L,
                                managerBranch1.getId(), "Duyệt"));

                // Status KHÔNG được chuyển sang ACTIVE
                assertNotEquals(ContractStatus.ACTIVE, samplePendingContract.getStatus());
        }

        // =========================================================================
        // 3. STEP 2 (CHECKER): REJECTION
        // =========================================================================

        @Test
        @DisplayName("rejectContract - Quản lý từ chối: Trạng thái CANCELLED, ghi rõ lý do, KHÔNG xuất quỹ")
        void rejectContract_ByManager_SetsStatusCancelled_ZeroCashMovement() {
                when(userRepository.findByIdAndDeletedFalse(managerBranch1.getId()))
                                .thenReturn(Optional.of(managerBranch1));
                when(contractRepository.findByIdAndDeletedFalse(500L)).thenReturn(Optional.of(samplePendingContract));

                String rejectReason = "Khách hàng không cung cấp được giấy tờ gốc để đối chiếu";
                ContractResponse response = contractService.rejectContract(500L, managerBranch1.getId(), rejectReason);

                assertNotNull(response);
                assertEquals(ContractStatus.CANCELLED, samplePendingContract.getStatus());
                assertTrue(samplePendingContract.getApprovalNotes().contains(rejectReason));

                verify(cashbookService, never()).disburseLoan(any(), any(), any(), any(), any());
                verify(contractRepository, times(1)).save(samplePendingContract);
        }

        @Test
        @DisplayName("rejectContract - STAFF hoặc MANAGER chi nhánh khác cố tình từ chối ném FORBIDDEN")
        void rejectContract_UnauthorizedRole_ThrowsForbidden() {
                when(userRepository.findByIdAndDeletedFalse(managerBranch2.getId()))
                                .thenReturn(Optional.of(managerBranch2));
                when(contractRepository.findByIdAndDeletedFalse(500L)).thenReturn(Optional.of(samplePendingContract));

                BusinessException ex = assertThrows(BusinessException.class, () -> contractService.rejectContract(500L,
                                managerBranch2.getId(), "Từ chối trái quyền"));

                assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
                assertEquals(ContractStatus.PENDING_APPROVAL, samplePendingContract.getStatus());
        }

        // =========================================================================
        // 4. SYSTEM-ENFORCED TODAY START DATE & APPROVAL DATE RECALCULATION
        // =========================================================================

        @Test
        @DisplayName("createContract - Bỏ qua startDate client gửi lên, luôn ép theo ngày hiện tại của server clock")
        void createContract_IgnoresClientSuppliedStartDate_AlwaysUsesServerClockToday() {
                ContractCreateRequest tamperedRequest = ContractCreateRequest.builder()
                                .customerId(100L)
                                .collateralId(200L)
                                .branchId(1L)
                                .principalAmount(new BigDecimal("30000000.00"))
                                .interestType(InterestType.DAILY_PER_MILLION)
                                .interestRate(new BigDecimal("2000.00"))
                                .storageFeeRate(new BigDecimal("500.00"))
                                .loanTermDays(30)
                                .paymentCycleDays(10)
                                .startDate(LocalDate.of(2025, 1, 1))
                                .notes("Client gửi ngày quá khứ")
                                .build();

                when(customerRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(customer));
                when(collateralRepository.findByIdAndDeletedFalse(200L)).thenReturn(Optional.of(collateral));
                when(branchRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(branch1));
                when(contractRepository.save(any(ContractEntity.class))).thenAnswer(inv -> {
                        ContractEntity entity = inv.getArgument(0);
                        entity.setId(501L);
                        return entity;
                });

                ContractResponse response = contractService.createContract(tamperedRequest, staffBranch1.getId());

                assertEquals(LocalDate.of(2026, 11, 10), response.getStartDate());
                assertEquals(LocalDate.of(2026, 11, 20), response.getNextInterestDueDate());
                assertEquals(LocalDate.of(2026, 12, 10), response.getMaturityDate());
        }

        @Test
        @DisplayName("approveAndDisburseContract - Duyệt sang ngày/tháng mới: tính lại startDate, nextInterestDueDate, maturityDate theo ngày duyệt và giữ nguyên contractCode")
        void approveAndDisburseContract_ApprovedOnLaterDate_RecalculatesDatesFromApprovalDateAndPreservesContractCode() {
                LocalDateTime originalCreatedAt = LocalDateTime.of(2026, 11, 30, 17, 45);
                ContractEntity carryOverContract = ContractEntity.builder()
                                .contractCode("HD-2611-B01-ABC123")
                                .customer(customer)
                                .collateral(collateral)
                                .branch(branch1)
                                .principalAmount(new BigDecimal("30000000.00"))
                                .remainingPrincipal(new BigDecimal("30000000.00"))
                                .appliedLtvPercent(new BigDecimal("60.00"))
                                .interestType(InterestType.DAILY_PER_MILLION)
                                .interestRate(new BigDecimal("2000.00"))
                                .storageFeeRate(new BigDecimal("500.00"))
                                .loanTermDays(30)
                                .paymentCycleDays(10)
                                .startDate(LocalDate.of(2026, 11, 30))
                                .maturityDate(LocalDate.of(2026, 12, 30))
                                .nextInterestDueDate(LocalDate.of(2026, 12, 10))
                                .status(ContractStatus.PENDING_APPROVAL)
                                .totalInterestPaid(BigDecimal.ZERO)
                                .build();
                carryOverContract.setId(550L);
                carryOverContract.setCreatedAt(originalCreatedAt);

                LocalDateTime approvalClockTime = LocalDateTime.of(2026, 12, 1, 10, 15);
                contractService.setClock(Clock.fixed(
                                approvalClockTime.atZone(ZoneId.systemDefault()).toInstant(),
                                ZoneId.systemDefault()));

                when(userRepository.findByIdAndDeletedFalse(managerBranch1.getId()))
                                .thenReturn(Optional.of(managerBranch1));
                when(contractRepository.findByIdAndDeletedFalse(550L)).thenReturn(Optional.of(carryOverContract));
                when(collateralRepository.save(any(CollateralEntity.class))).thenAnswer(inv -> inv.getArgument(0));
                when(cashbookService.disburseLoan(any(), any(), any(), any(), any()))
                                .thenReturn(CashbookResponse.builder().id(901L).build());
                when(contractRepository.save(any(ContractEntity.class))).thenAnswer(inv -> inv.getArgument(0));

                ContractResponse response = contractService.approveAndDisburseContract(550L, managerBranch1.getId(),
                                "Duyệt qua ngày hôm sau");

                assertEquals(ContractStatus.ACTIVE, response.getStatus());
                assertEquals("HD-2611-B01-ABC123", response.getContractCode());
                assertEquals(LocalDate.of(2026, 12, 1), response.getStartDate());
                assertEquals(LocalDate.of(2026, 12, 11), response.getNextInterestDueDate());
                assertEquals(LocalDate.of(2026, 12, 31), response.getMaturityDate());
                assertEquals(approvalClockTime, carryOverContract.getApprovedAt());
                assertEquals(originalCreatedAt, carryOverContract.getCreatedAt());
        }

        @Test
        @DisplayName("approveAndDisburseContract - Duyệt trong cùng ngày tạo giữ nguyên bộ 3 mốc ngày hiện tại")
        void approveAndDisburseContract_SameDayApproval_KeepsTodaySchedule() {
                when(userRepository.findByIdAndDeletedFalse(managerBranch1.getId()))
                                .thenReturn(Optional.of(managerBranch1));
                when(contractRepository.findByIdAndDeletedFalse(500L)).thenReturn(Optional.of(samplePendingContract));
                when(collateralRepository.save(any(CollateralEntity.class))).thenAnswer(inv -> inv.getArgument(0));
                when(cashbookService.disburseLoan(any(), any(), any(), any(), any()))
                                .thenReturn(CashbookResponse.builder().id(902L).build());
                when(contractRepository.save(any(ContractEntity.class))).thenAnswer(inv -> inv.getArgument(0));

                ContractResponse response = contractService.approveAndDisburseContract(500L, managerBranch1.getId(),
                                "Duyệt trong ngày");

                assertEquals(ContractStatus.ACTIVE, response.getStatus());
                assertEquals(LocalDate.of(2026, 11, 10), response.getStartDate());
                assertEquals(LocalDate.of(2026, 11, 20), response.getNextInterestDueDate());
                assertEquals(LocalDate.of(2026, 12, 10), response.getMaturityDate());
        }

        @Test
        @DisplayName("ContractRepository JPQL - Đảm bảo các query Dashboard luôn giữ hiển thị hồ sơ PENDING_APPROVAL từ ngày/tháng trước")
        void contractRepositoryQueries_IncludeCarryOverPendingApprovalContracts() {
                for (String methodName : new String[] {
                                "findDashboardMonthlyContracts",
                                "findDashboardContractPage",
                                "countDashboardTabs",
                                "aggregatePeriodKpis"
                }) {
                        Query queryAnno = Arrays.stream(ContractRepository.class.getDeclaredMethods())
                                        .filter(m -> m.getName().equals(methodName))
                                        .findFirst()
                                        .map(m -> m.getAnnotation(Query.class))
                                        .orElseThrow(() -> new AssertionError("Missing @Query on " + methodName));

                        assertTrue(queryAnno.value().contains("PENDING_APPROVAL"),
                                        methodName + " JPQL must include PENDING_APPROVAL carry-over clause");
                        if ("findDashboardContractPage".equals(methodName)) {
                                assertTrue(queryAnno.countQuery().contains("PENDING_APPROVAL"),
                                                "findDashboardContractPage countQuery must include PENDING_APPROVAL");
                        }
                }
        }
}

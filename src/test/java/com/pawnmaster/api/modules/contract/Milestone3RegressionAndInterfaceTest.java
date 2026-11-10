package com.pawnmaster.api.modules.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pawnmaster.api.common.BaseResponse;
import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.cashbook.CashbookController;
import com.pawnmaster.api.modules.cashbook.CashbookService;
import com.pawnmaster.api.modules.cashbook.TransactionCategory;
import com.pawnmaster.api.modules.cashbook.TransactionType;
import com.pawnmaster.api.modules.cashbook.dto.CashFlowSummaryResponse;
import com.pawnmaster.api.modules.cashbook.dto.CashbookCreateRequest;
import com.pawnmaster.api.modules.cashbook.dto.CashbookResponse;
import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.collateral.CollateralEntity;
import com.pawnmaster.api.modules.collateral.CollateralStatus;
import com.pawnmaster.api.modules.contract.dto.ContractApprovalRequest;
import com.pawnmaster.api.modules.contract.dto.ContractCreateRequest;
import com.pawnmaster.api.modules.contract.dto.ContractRedeemRequest;
import com.pawnmaster.api.modules.contract.dto.ContractRejectRequest;
import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import com.pawnmaster.api.modules.contract.dto.InterestCalculationResponse;
import com.pawnmaster.api.modules.contract.dto.InterestCollectionRequest;
import com.pawnmaster.api.modules.contract.dto.InterestPaymentResponse;
import com.pawnmaster.api.modules.contract.dto.LiquidationRequest;
import com.pawnmaster.api.modules.customer.CustomerEntity;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.CustomerStatus;
import com.pawnmaster.api.modules.customer.VerificationDocumentType;
import com.pawnmaster.api.modules.user.RoleEnum;
import com.pawnmaster.api.modules.user.UserEntity;
import com.pawnmaster.api.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Challenger 2: Milestone 3 Whole-System Non-Regression and Interface Stability Suite.
 *
 * Verifies:
 * 1. ContractController REST endpoints required by Milestone 4 Dashboard:
 *    - POST /api/v1/contracts/{id}/approve (Quick contract approval)
 *    - POST /api/v1/contracts/{id}/reject (Quick contract rejection)
 *    - GET /api/v1/contracts (Multi-branch filtering by status, branchId, customerId)
 *    - Servicing & lifecycle endpoints (/interest, /calculate-interest, /redeem, /liquidate, /mark-overdue)
 * 2. CashbookController REST endpoints required by Milestone 4 Dashboard:
 *    - GET /api/v1/cashbook/branches/{branchId}/summary and /summary
 *    - GET /api/v1/cashbook/transactions
 * 3. Model schema contract stability:
 *    - ContractResponse and CashFlowSummaryResponse completeness for dashboard.html and dashboard.js
 * 4. Post-disbursement financial calculation boundary resilience across 3 interest types
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Challenger 2: Milestone 3 Non-Regression & Interface Stability Suite")
public class Milestone3RegressionAndInterfaceTest {

    private MockMvc contractMockMvc;
    private MockMvc cashbookMockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private ContractService contractService;

    @Mock
    private CashbookService cashbookService;

    private ContractResponse sampleContractResponse;
    private CashFlowSummaryResponse sampleSummaryResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        ContractController contractController = new ContractController(contractService);
        CashbookController cashbookController = new CashbookController(cashbookService);

        HandlerMethodArgumentResolver principalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().equals(UserPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return UserPrincipal.builder()
                        .id(2L)
                        .username("manager_q1")
                        .role(RoleEnum.MANAGER)
                        .branchId(1L)
                        .branchName("Chi nhánh Quận 1")
                        .build();
            }
        };

        contractMockMvc = MockMvcBuilders.standaloneSetup(contractController)
                .setCustomArgumentResolvers(principalResolver)
                .build();
        cashbookMockMvc = MockMvcBuilders.standaloneSetup(cashbookController)
                .setCustomArgumentResolvers(principalResolver)
                .build();

        sampleContractResponse = ContractResponse.builder()
                .id(101L)
                .contractCode("HD-2026-0001")
                .branchId(1L)
                .branchCode("CN-HCM-01")
                .branchName("Chi nhánh Quận 1")
                .customerId(10L)
                .customerCode("KH-0001")
                .customerName("Nguyễn Văn An")
                .customerPhone("0901234567")
                .collateralId(20L)
                .collateralCode("TS-0001")
                .collateralName("Honda SH 150i 2023")
                .collateralCategory(CollateralCategory.MOTORBIKE)
                .collateralAppraisedValue(new BigDecimal("60000000"))
                .principalAmount(new BigDecimal("30000000"))
                .remainingPrincipal(new BigDecimal("30000000"))
                .appliedLtvPercent(new BigDecimal("0.5000"))
                .interestType(InterestType.DAILY_PER_MILLION)
                .interestRate(new BigDecimal("2000"))
                .storageFeeRate(new BigDecimal("500"))
                .loanTermDays(30)
                .paymentCycleDays(10)
                .startDate(LocalDate.of(2026, 11, 1))
                .maturityDate(LocalDate.of(2026, 12, 1))
                .nextInterestDueDate(LocalDate.of(2026, 11, 11))
                .totalInterestPaid(BigDecimal.ZERO)
                .status(ContractStatus.PENDING_APPROVAL)
                .build();

        sampleSummaryResponse = CashFlowSummaryResponse.builder()
                .branchId(1L)
                .branchName("Chi nhánh Quận 1")
                .currentCashBalance(new BigDecimal("470000000"))
                .totalCashIn(new BigDecimal("20000000"))
                .totalCashOut(new BigDecimal("50000000"))
                .netCashFlow(new BigDecimal("-30000000"))
                .disbursementTotal(new BigDecimal("50000000"))
                .interestCollectionTotal(new BigDecimal("5000000"))
                .principalCollectionTotal(new BigDecimal("15000000"))
                .liquidationIncomeTotal(BigDecimal.ZERO)
                .transactionCount(3L)
                .build();
    }

    @Nested
    @DisplayName("1. ContractController REST Endpoint Verification for Milestone 4")
    class ContractControllerEndpointTests {

        @Test
        @DisplayName("POST /api/v1/contracts/{id}/approve - Quản lý duyệt hợp đồng thành công trả về 200")
        void approveContract_Success() throws Exception {
            ContractResponse approvedResponse = ContractResponse.builder()
                    .id(101L)
                    .contractCode("HD-2026-0001")
                    .branchId(1L)
                    .status(ContractStatus.ACTIVE)
                    .approvedBy(2L)
                    .approvedAt(LocalDateTime.now())
                    .approvalNotes("Duyệt giải ngân nhanh")
                    .build();

            when(contractService.approveAndDisburseContract(eq(101L), anyLong(), anyString()))
                    .thenReturn(approvedResponse);

            ContractApprovalRequest req = new ContractApprovalRequest("Duyệt giải ngân nhanh");

            contractMockMvc.perform(post("/api/v1/contracts/101/approve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                    .andExpect(jsonPath("$.data.id").value(101))
                    .andExpect(jsonPath("$.data.contractCode").value("HD-2026-0001"))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.message").value(containsString("thành công")));

            verify(contractService, times(1)).approveAndDisburseContract(eq(101L), anyLong(), eq("Duyệt giải ngân nhanh"));
        }

        @Test
        @DisplayName("POST /api/v1/contracts/{id}/approve - Chấp nhận request body rỗng với ghi chú mặc định")
        void approveContract_NullRequestBody_DefaultsNotes() throws Exception {
            ContractResponse approvedResponse = ContractResponse.builder()
                    .id(101L)
                    .status(ContractStatus.ACTIVE)
                    .build();

            when(contractService.approveAndDisburseContract(eq(101L), anyLong(), anyString()))
                    .thenReturn(approvedResponse);

            contractMockMvc.perform(post("/api/v1/contracts/101/approve"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));

            verify(contractService, times(1)).approveAndDisburseContract(eq(101L), anyLong(), eq("Phê duyệt giải ngân hợp đồng"));
        }

        @Test
        @DisplayName("POST /api/v1/contracts/{id}/reject - Quản lý từ chối hợp đồng với lý do hợp lệ trả về 200")
        void rejectContract_Success() throws Exception {
            ContractResponse rejectedResponse = ContractResponse.builder()
                    .id(101L)
                    .contractCode("HD-2026-0001")
                    .status(ContractStatus.CANCELLED)
                    .build();

            when(contractService.rejectContract(eq(101L), anyLong(), eq("Hồ sơ thiếu xác minh cư trú")))
                    .thenReturn(rejectedResponse);

            ContractRejectRequest req = new ContractRejectRequest("Hồ sơ thiếu xác minh cư trú");

            contractMockMvc.perform(post("/api/v1/contracts/101/reject")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.message").value(containsString("Từ chối")));

            verify(contractService, times(1)).rejectContract(eq(101L), anyLong(), eq("Hồ sơ thiếu xác minh cư trú"));
        }

        @Test
        @DisplayName("POST /api/v1/contracts/{id}/reject - Báo lỗi 400 Bad Request khi thiếu lý do từ chối")
        void rejectContract_BlankReason_ReturnsBadRequest() throws Exception {
            ContractRejectRequest req = new ContractRejectRequest("");

            contractMockMvc.perform(post("/api/v1/contracts/101/reject")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());

            verify(contractService, never()).rejectContract(anyLong(), anyLong(), anyString());
        }

        @Test
        @DisplayName("GET /api/v1/contracts?branchId=1&status=PENDING_APPROVAL - Lọc hợp đồng theo chi nhánh và trạng thái")
        void getContracts_ByBranchAndStatus() throws Exception {
            when(contractService.getContractsByBranchAndStatus(1L, ContractStatus.PENDING_APPROVAL))
                    .thenReturn(Collections.singletonList(sampleContractResponse));

            contractMockMvc.perform(get("/api/v1/contracts")
                            .param("branchId", "1")
                            .param("status", "PENDING_APPROVAL"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].contractCode").value("HD-2026-0001"))
                    .andExpect(jsonPath("$.data[0].status").value("PENDING_APPROVAL"));

            verify(contractService, times(1)).getContractsByBranchAndStatus(1L, ContractStatus.PENDING_APPROVAL);
        }

        @Test
        @DisplayName("GET /api/v1/contracts?branchId=1 - Lọc hợp đồng theo chi nhánh")
        void getContracts_ByBranchOnly() throws Exception {
            when(contractService.getContractsByBranch(1L))
                    .thenReturn(Collections.singletonList(sampleContractResponse));

            contractMockMvc.perform(get("/api/v1/contracts")
                            .param("branchId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].branchId").value(1));

            verify(contractService, times(1)).getContractsByBranch(1L);
        }

        @Test
        @DisplayName("GET /api/v1/contracts?status=ACTIVE - Lọc hợp đồng toàn hệ thống theo trạng thái")
        void getContracts_ByStatusOnly() throws Exception {
            when(contractService.getContractsByStatus(ContractStatus.ACTIVE))
                    .thenReturn(Collections.singletonList(sampleContractResponse));

            contractMockMvc.perform(get("/api/v1/contracts")
                            .param("status", "ACTIVE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)));

            verify(contractService, times(1)).getContractsByStatus(ContractStatus.ACTIVE);
        }

        @Test
        @DisplayName("GET /api/v1/contracts - Lấy toàn bộ danh sách hợp đồng khi không có tham số")
        void getContracts_All() throws Exception {
            when(contractService.getAllContracts())
                    .thenReturn(Collections.singletonList(sampleContractResponse));

            contractMockMvc.perform(get("/api/v1/contracts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)));

            verify(contractService, times(1)).getAllContracts();
        }

        @Test
        @DisplayName("GET /api/v1/contracts/{id} - Lấy chi tiết hợp đồng theo ID")
        void getContractById_Success() throws Exception {
            when(contractService.getContractById(101L)).thenReturn(sampleContractResponse);

            contractMockMvc.perform(get("/api/v1/contracts/101"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.contractCode").value("HD-2026-0001"))
                    .andExpect(jsonPath("$.data.principalAmount").value(30000000));

            verify(contractService, times(1)).getContractById(101L);
        }

        @Test
        @DisplayName("GET /api/v1/contracts/health - Kiểm tra health endpoint của Contract module")
        void healthCheck_Success() throws Exception {
            contractMockMvc.perform(get("/api/v1/contracts/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value("Contract module is ready"));
        }
    }

    @Nested
    @DisplayName("2. CashbookController REST Endpoint Verification for Milestone 4")
    class CashbookControllerEndpointTests {

        @Test
        @DisplayName("GET /api/v1/cashbook/branches/{branchId}/summary - Truy vấn tổng hợp thu chi theo chi nhánh")
        void getBranchSummary_ByPathVariable_Success() throws Exception {
            when(cashbookService.getBranchCashFlowSummary(1L)).thenReturn(sampleSummaryResponse);

            cashbookMockMvc.perform(get("/api/v1/cashbook/branches/1/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.branchId").value(1))
                    .andExpect(jsonPath("$.data.currentCashBalance").value(470000000))
                    .andExpect(jsonPath("$.data.totalCashIn").value(20000000))
                    .andExpect(jsonPath("$.data.totalCashOut").value(50000000))
                    .andExpect(jsonPath("$.data.netCashFlow").value(-30000000));

            verify(cashbookService, times(1)).getBranchCashFlowSummary(1L);
        }

        @Test
        @DisplayName("GET /api/v1/cashbook/summary?branchIdParam=1 - Truy vấn tổng hợp qua query param branchIdParam")
        void getBranchSummary_ByQueryParam_Success() throws Exception {
            when(cashbookService.getBranchCashFlowSummary(1L)).thenReturn(sampleSummaryResponse);

            cashbookMockMvc.perform(get("/api/v1/cashbook/summary")
                            .param("branchIdParam", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.branchId").value(1))
                    .andExpect(jsonPath("$.data.currentCashBalance").value(470000000));

            verify(cashbookService, times(1)).getBranchCashFlowSummary(1L);
        }

        @Test
        @DisplayName("GET /api/v1/cashbook/summary - Không truyền param trả về summary rỗng an toàn")
        void getBranchSummary_NoParam_ReturnsEmptySummary() throws Exception {
            cashbookMockMvc.perform(get("/api/v1/cashbook/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.currentCashBalance").value(0))
                    .andExpect(jsonPath("$.data.totalCashIn").value(0))
                    .andExpect(jsonPath("$.data.totalCashOut").value(0));

            verify(cashbookService, never()).getBranchCashFlowSummary(anyLong());
        }

        @Test
        @DisplayName("GET /api/v1/cashbook/transactions?branchId=1&type=CASH_OUT - Lọc danh sách phiếu thu/chi")
        void getTransactions_ByBranchAndType() throws Exception {
            CashbookResponse voucher = CashbookResponse.builder()
                    .id(1L)
                    .transactionCode("PC-202611-0001")
                    .branchId(1L)
                    .transactionType(TransactionType.CASH_OUT)
                    .amount(new BigDecimal("30000000"))
                    .balanceAfter(new BigDecimal("470000000"))
                    .build();

            when(cashbookService.getTransactionsByBranchAndType(1L, TransactionType.CASH_OUT))
                    .thenReturn(Collections.singletonList(voucher));

            cashbookMockMvc.perform(get("/api/v1/cashbook/transactions")
                            .param("branchId", "1")
                            .param("type", "CASH_OUT"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].transactionCode").value("PC-202611-0001"))
                    .andExpect(jsonPath("$.data[0].transactionType").value("CASH_OUT"));

            verify(cashbookService, times(1)).getTransactionsByBranchAndType(1L, TransactionType.CASH_OUT);
        }
    }

    @Nested
    @DisplayName("3. Schema & Model Contract Stability for Dashboard UI")
    class SchemaStabilityTests {

        @Test
        @DisplayName("ContractResponse contains all required fields for Dashboard UI table & badges")
        void contractResponse_HasAllRequiredDashboardFields() {
            assertNotNull(sampleContractResponse.getId());
            assertNotNull(sampleContractResponse.getContractCode());
            assertNotNull(sampleContractResponse.getBranchId());
            assertNotNull(sampleContractResponse.getBranchCode());
            assertNotNull(sampleContractResponse.getBranchName());
            assertNotNull(sampleContractResponse.getCustomerName());
            assertNotNull(sampleContractResponse.getCustomerPhone());
            assertNotNull(sampleContractResponse.getCollateralName());
            assertNotNull(sampleContractResponse.getCollateralCategory());
            assertNotNull(sampleContractResponse.getPrincipalAmount());
            assertNotNull(sampleContractResponse.getRemainingPrincipal());
            assertNotNull(sampleContractResponse.getAppliedLtvPercent());
            assertNotNull(sampleContractResponse.getInterestType());
            assertNotNull(sampleContractResponse.getInterestRate());
            assertNotNull(sampleContractResponse.getStorageFeeRate());
            assertNotNull(sampleContractResponse.getLoanTermDays());
            assertNotNull(sampleContractResponse.getPaymentCycleDays());
            assertNotNull(sampleContractResponse.getStartDate());
            assertNotNull(sampleContractResponse.getMaturityDate());
            assertNotNull(sampleContractResponse.getNextInterestDueDate());
            assertNotNull(sampleContractResponse.getStatus());
        }

        @Test
        @DisplayName("CashFlowSummaryResponse contains all required KPI fields for Dashboard cards & charts")
        void cashFlowSummaryResponse_HasAllRequiredKpiFields() {
            assertNotNull(sampleSummaryResponse.getBranchId());
            assertNotNull(sampleSummaryResponse.getBranchName());
            assertNotNull(sampleSummaryResponse.getCurrentCashBalance());
            assertNotNull(sampleSummaryResponse.getTotalCashIn());
            assertNotNull(sampleSummaryResponse.getTotalCashOut());
            assertNotNull(sampleSummaryResponse.getNetCashFlow());
            assertNotNull(sampleSummaryResponse.getDisbursementTotal());
            assertNotNull(sampleSummaryResponse.getInterestCollectionTotal());
            assertNotNull(sampleSummaryResponse.getPrincipalCollectionTotal());
            assertNotNull(sampleSummaryResponse.getLiquidationIncomeTotal());
            assertNotNull(sampleSummaryResponse.getTransactionCount());
        }

        @ParameterizedTest
        @EnumSource(ContractStatus.class)
        @DisplayName("ContractStatus enum supports all required lifecycle states")
        void contractStatus_EnumCompleteness(ContractStatus status) {
            assertNotNull(status);
            assertTrue(Arrays.asList(
                    ContractStatus.PENDING_APPROVAL,
                    ContractStatus.ACTIVE,
                    ContractStatus.OVERDUE,
                    ContractStatus.EXTENDED,
                    ContractStatus.REDEEMED,
                    ContractStatus.LIQUIDATED,
                    ContractStatus.CANCELLED
            ).contains(status));
        }
    }

    @Nested
    @DisplayName("4. Post-Disbursement Mathematical Invariant & Resilience Verification")
    class MathematicalInvariantTests {

        private ContractServiceImpl contractServiceImpl;

        @BeforeEach
        void initService() {
            contractServiceImpl = new ContractServiceImpl(
                    null, null, null, null, null, null, null, null
            );
        }

        @ParameterizedTest
        @CsvSource({
                "10000000, 2000, 500, 10, 250000.00",    // 10M, 2k rate, 500 storage, 10 days = 10 * 25k = 250,000
                "50000000, 1500, 500, 30, 3000000.00",   // 50M, 1.5k rate, 500 storage, 30 days = 50 * 2k * 30 = 3,000,000
                "1000000, 3000, 0, 1, 3000.00",          // 1M, 3k rate, 1 day = 3,000
                "0, 2000, 500, 10, 0.00"                 // 0 principal = 0 interest
        })
        @DisplayName("DAILY_PER_MILLION interest calculation exactness")
        void dailyPerMillion_CalculatesExactInterest(
                BigDecimal principal, BigDecimal rate, BigDecimal storageFee, int days, BigDecimal expectedInterest
        ) {
            ContractEntity contract = ContractEntity.builder()
                    .remainingPrincipal(principal)
                    .interestType(InterestType.DAILY_PER_MILLION)
                    .interestRate(rate)
                    .storageFeeRate(storageFee)
                    .build();

            LocalDate from = LocalDate.of(2026, 11, 1);
            LocalDate to = from.plusDays(days);

            InterestCalculationResponse response = contractServiceImpl.calculateInterest(contract, from, to);
            assertEquals(expectedInterest, response.getTotalDue());
        }

        @ParameterizedTest
        @CsvSource({
                "10000000, 3.0, 1.0, 30, 400000.00",     // 10M, 3% interest + 1% storage = 4% for 30 days = 400,000
                "20000000, 2.5, 0.5, 15, 300000.00",     // 20M, 3% total for 15 days (half month) = 300,000
                "100000000, 1.5, 0.5, 60, 4000000.00"    // 100M, 2% total for 60 days = 4,000,000
        })
        @DisplayName("PERCENT_MONTHLY interest calculation exactness")
        void percentMonthly_CalculatesExactInterest(
                BigDecimal principal, BigDecimal rate, BigDecimal storageFee, int days, BigDecimal expectedInterest
        ) {
            ContractEntity contract = ContractEntity.builder()
                    .remainingPrincipal(principal)
                    .interestType(InterestType.PERCENT_MONTHLY)
                    .interestRate(rate)
                    .storageFeeRate(storageFee)
                    .build();

            LocalDate from = LocalDate.of(2026, 11, 1);
            LocalDate to = from.plusDays(days);

            InterestCalculationResponse response = contractServiceImpl.calculateInterest(contract, from, to);
            assertEquals(expectedInterest, response.getTotalDue());
        }

        @ParameterizedTest
        @CsvSource({
                "10000000, 1.0, 0.2, 7, 120000.00",      // 10M, 1.2% total for 7 days (1 week) = 120,000
                "14000000, 1.0, 0.5, 14, 420000.00"     // 14M, 1.5% total for 14 days (2 weeks) = 420,000
        })
        @DisplayName("PERCENT_WEEKLY interest calculation exactness")
        void percentWeekly_CalculatesExactInterest(
                BigDecimal principal, BigDecimal rate, BigDecimal storageFee, int days, BigDecimal expectedInterest
        ) {
            ContractEntity contract = ContractEntity.builder()
                    .remainingPrincipal(principal)
                    .interestType(InterestType.PERCENT_WEEKLY)
                    .interestRate(rate)
                    .storageFeeRate(storageFee)
                    .build();

            LocalDate from = LocalDate.of(2026, 11, 1);
            LocalDate to = from.plusDays(days);

            InterestCalculationResponse response = contractServiceImpl.calculateInterest(contract, from, to);
            assertEquals(expectedInterest, response.getTotalDue());
        }
    }
}

package com.pawnmaster.api.modules.dashboard;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.common.GlobalExceptionHandler;
import com.pawnmaster.api.modules.branch.BranchService;
import com.pawnmaster.api.modules.branch.dto.BranchResponse;
import com.pawnmaster.api.modules.cashbook.CashbookService;
import com.pawnmaster.api.modules.cashbook.dto.CashFlowSummaryResponse;
import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.collateral.CollateralService;
import com.pawnmaster.api.modules.collateral.LtvPolicyService;
import com.pawnmaster.api.modules.contract.ContractService;
import com.pawnmaster.api.modules.contract.ContractStatus;
import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.CustomerService;
import com.pawnmaster.api.modules.customer.VerificationDocumentType;
import com.pawnmaster.api.modules.dashboard.dto.DashboardContractPageResponse;
import com.pawnmaster.api.modules.dashboard.dto.DashboardSummaryResponse;
import com.pawnmaster.api.modules.user.RoleEnum;
import com.pawnmaster.api.security.BranchSecurityGuard;
import com.pawnmaster.api.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

        @Mock
        private DashboardService dashboardService;

        @Mock
        private BranchService branchService;

        @Mock
        private ContractService contractService;

        @Mock
        private CashbookService cashbookService;

        @Mock
        private CustomerService customerService;

        @Mock
        private CollateralService collateralService;

        @Mock
        private LtvPolicyService ltvPolicyService;

        private DashboardController dashboardController;

        private MockMvc mockMvc;

        private BranchResponse branch1;
        private BranchResponse branch2;

        @BeforeEach
        void setUp() {
                InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
                viewResolver.setPrefix("/templates/");
                viewResolver.setSuffix(".html");

                dashboardController = new DashboardController(dashboardService, branchService, new BranchSecurityGuard());
                dashboardController.setContractService(contractService);
                dashboardController.setCashbookService(cashbookService);

                mockMvc = MockMvcBuilders.standaloneSetup(dashboardController)
                                .setViewResolvers(viewResolver)
                                .setControllerAdvice(new GlobalExceptionHandler())
                                .build();

                branch1 = BranchResponse.builder()
                                .id(1L)
                                .branchCode("CN01")
                                .branchName("Chi nhánh Quận 1")
                                .provinceName("TP. Hồ Chí Minh")
                                .districtName("Quận 1")
                                .capitalLimit(new BigDecimal("2500000000"))
                                .currentCashBalance(new BigDecimal("520000000"))
                                .active(true)
                                .build();

                branch2 = BranchResponse.builder()
                                .id(2L)
                                .branchCode("CN02")
                                .branchName("Chi nhánh Quận Bình Thạnh")
                                .provinceName("TP. Hồ Chí Minh")
                                .districtName("Bình Thạnh")
                                .capitalLimit(new BigDecimal("2000000000"))
                                .currentCashBalance(new BigDecimal("350000000"))
                                .active(true)
                                .build();

                SecurityContextHolder.clearContext();
        }

        @AfterEach
        void tearDown() {
                SecurityContextHolder.clearContext();
        }

        private List<ContractResponse> generateMockContracts(int count, int startId) {
                List<ContractResponse> list = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                        int id = startId + i;
                        list.add(ContractResponse.builder()
                                        .id((long) id)
                                        .contractCode(String.format("HD-%04d", id))
                                        .customerName("Khách hàng " + id)
                                        .status(ContractStatus.ACTIVE)
                                        .principalAmount(new BigDecimal("10000000"))
                                        .remainingPrincipal(new BigDecimal("10000000"))
                                        .build());
                }
                return list;
        }

        @Test
        @DisplayName("GET /dashboard without branchId should select first branch and populate all model attributes")
        void testDashboardDefaultBranch() {
                DashboardSummaryResponse mockSummary = DashboardSummaryResponse.builder()
                                .selectedBranchId(1L)
                                .selectedBranchCode("CN01")
                                .selectedBranchName("Chi nhánh Quận 1")
                                .currentCashBalance(new BigDecimal("520000000"))
                                .capitalLimit(new BigDecimal("2500000000"))
                                .branches(Arrays.asList(branch1, branch2))
                                .selectedTimeframe(DashboardTimeframe.THIS_MONTH)
                                .timeframeLabel("Tháng này")
                                .kpi(DashboardSummaryResponse.KpiSummary.builder()
                                                .activeContractsCount(5L)
                                                .disbursedPrincipalAmount(new BigDecimal("100000000"))
                                                .build())
                                .contractPage(DashboardContractPageResponse.empty(10, "ALL", null))
                                .contracts(Collections.emptyList())
                                .build();

                when(dashboardService.getDashboardSummary(isNull(), eq(DashboardTimeframe.THIS_MONTH), eq(10)))
                                .thenReturn(mockSummary);

                Model model = new ConcurrentModel();
                String view = dashboardController.dashboard(null, model);

                assertThat(view).isEqualTo("dashboard");
                assertThat(model.getAttribute("branches")).isEqualTo(Arrays.asList(branch1, branch2));
                assertThat(model.getAttribute("selectedBranch")).isEqualTo(branch1);
                assertThat(model.getAttribute("kpi")).isNotNull();
                assertThat(model.getAttribute("recentContracts")).isNotNull();
                assertThat(model.getAttribute("cashFlowSummary")).isNotNull();
                assertThat(model.getAttribute("occupationGroups")).isEqualTo(CustomerOccupationGroup.values());
                assertThat(model.getAttribute("verificationDocTypes")).isEqualTo(VerificationDocumentType.values());
                assertThat(model.getAttribute("collateralCategories")).isEqualTo(CollateralCategory.values());
        }

        @Test
        @DisplayName("GET /dashboard with branchId=2 should select branch 2 and scope contracts")
        void testDashboardSpecificBranch() {
                DashboardSummaryResponse mockSummary = DashboardSummaryResponse.builder()
                                .selectedBranchId(2L)
                                .selectedBranchCode("CN02")
                                .selectedBranchName("Chi nhánh Quận Bình Thạnh")
                                .currentCashBalance(new BigDecimal("350000000"))
                                .branches(Arrays.asList(branch1, branch2))
                                .selectedTimeframe(DashboardTimeframe.THIS_MONTH)
                                .timeframeLabel("Tháng này")
                                .contractPage(DashboardContractPageResponse.empty(10, "ALL", null))
                                .contracts(Collections.emptyList())
                                .build();

                when(dashboardService.getDashboardSummary(eq(2L), eq(DashboardTimeframe.THIS_MONTH), eq(10)))
                                .thenReturn(mockSummary);

                Model model = new ConcurrentModel();
                String view = dashboardController.dashboard(2L, model);

                assertThat(view).isEqualTo("dashboard");
                assertThat(model.getAttribute("selectedBranch")).isEqualTo(branch2);
                verify(contractService).getContractsByBranch(2L);
                verify(cashbookService).getBranchCashFlowSummary(2L);
        }

        @Test
        @DisplayName("GET /dashboard with unknown branchId should attempt to fetch by id")
        void testDashboardUnknownBranchId() {
                BranchResponse branch99 = BranchResponse.builder()
                                .id(99L)
                                .branchCode("CN99")
                                .branchName("Chi nhánh Đà Nẵng")
                                .currentCashBalance(new BigDecimal("100000000"))
                                .build();

                when(branchService.getBranchById(99L)).thenReturn(branch99);

                DashboardSummaryResponse mockSummary = DashboardSummaryResponse.builder()
                                .selectedBranchId(99L)
                                .branches(Arrays.asList(branch1))
                                .selectedTimeframe(DashboardTimeframe.THIS_MONTH)
                                .contractPage(DashboardContractPageResponse.empty(10, "ALL", null))
                                .contracts(Collections.emptyList())
                                .build();

                when(dashboardService.getDashboardSummary(eq(99L), eq(DashboardTimeframe.THIS_MONTH), eq(10)))
                                .thenReturn(mockSummary);

                Model model = new ConcurrentModel();
                String view = dashboardController.dashboard(99L, model);

                assertThat(view).isEqualTo("dashboard");
                assertThat(model.getAttribute("selectedBranch")).isEqualTo(branch99);
                verify(branchService).getBranchById(99L);
        }

        @Test
        @DisplayName("GET /dashboard when branches are empty should handle gracefully without crashing")
        void testDashboardEmptyBranchesFallback() {
                when(branchService.getActiveBranches()).thenReturn(Collections.emptyList());
                when(branchService.getAllBranches()).thenReturn(Collections.emptyList());
                when(dashboardService.getDashboardSummary(any(), any(), anyInt())).thenReturn(null);

                Model model = new ConcurrentModel();
                String view = dashboardController.dashboard(null, model);

                assertThat(view).isEqualTo("dashboard");
                assertThat(model.getAttribute("selectedBranch")).isNull();
                assertThat(model.getAttribute("branches")).isEqualTo(Collections.emptyList());
                assertThat(model.getAttribute("recentContracts")).isEqualTo(Collections.emptyList());
                assertThat(model.getAttribute("cashFlowSummary")).isNotNull();
        }

        @Test
        @DisplayName("GET /dashboard when service throws exception should fallback safely")
        void testDashboardServiceExceptionFallback() {
                when(branchService.getActiveBranches()).thenThrow(new RuntimeException("Database error"));
                when(dashboardService.getDashboardSummary(any(), any(), anyInt())).thenThrow(new RuntimeException("Service error"));

                Model model = new ConcurrentModel();
                String view = dashboardController.dashboard(null, model);

                assertThat(view).isEqualTo("dashboard");
                assertThat(model.getAttribute("selectedBranch")).isNull();
                assertThat(model.getAttribute("recentContracts")).isEqualTo(Collections.emptyList());
        }

        @Test
        @DisplayName("KPI calculation should aggregate active, overdue, and pending approval metrics")
        void testKpiCalculation() {
                DashboardSummaryResponse.KpiSummary kpiSummary = DashboardSummaryResponse.KpiSummary.builder()
                                .activeContractsCount(1L)
                                .disbursedPrincipalAmount(new BigDecimal("50000000"))
                                .collectedInterestAmount(new BigDecimal("3000000"))
                                .overdueContractsCount(1L)
                                .atRiskPrincipalAmount(new BigDecimal("20000000"))
                                .pendingApprovalCount(1L)
                                .expectedInterestAmount(BigDecimal.ZERO)
                                .build();

                DashboardSummaryResponse mockSummary = DashboardSummaryResponse.builder()
                                .selectedBranchId(1L)
                                .branches(Arrays.asList(branch1))
                                .kpi(kpiSummary)
                                .contractPage(DashboardContractPageResponse.empty(10, "ALL", null))
                                .contracts(Collections.emptyList())
                                .build();

                when(dashboardService.getDashboardSummary(eq(1L), eq(DashboardTimeframe.THIS_MONTH), eq(10)))
                                .thenReturn(mockSummary);

                Model model = new ConcurrentModel();
                dashboardController.dashboard(1L, model);

                @SuppressWarnings("unchecked")
                Map<String, Object> kpi = (Map<String, Object>) model.getAttribute("kpi");

                assertThat(kpi).isNotNull();
                assertThat(kpi.get("activeContractsCount")).isEqualTo("1");
                assertThat(kpi.get("overdueContractsCount")).isEqualTo("1 HĐ");
                assertThat(kpi.get("pendingApprovalCount")).isEqualTo(1L);
                assertThat(kpi.get("disbursedBalance")).isEqualTo(new BigDecimal("50000000"));
                assertThat(kpi.get("overdueAmount")).isEqualTo(new BigDecimal("20000000"));
        }

        @Test
        @DisplayName("MockMvc GET / and GET /dashboard should return view 'dashboard'")
        void testMockMvcEndpoints() throws Exception {
                when(branchService.getActiveBranches()).thenReturn(Arrays.asList(branch1));

                DashboardSummaryResponse mockSummary = DashboardSummaryResponse.builder()
                                .selectedBranchId(1L)
                                .branches(Arrays.asList(branch1))
                                .kpi(DashboardSummaryResponse.KpiSummary.builder().build())
                                .contractPage(DashboardContractPageResponse.empty(10, "ALL", null))
                                .contracts(Collections.emptyList())
                                .build();

                when(dashboardService.getDashboardSummary(any(), any(), anyInt())).thenReturn(mockSummary);

                mockMvc.perform(get("/"))
                                .andExpect(status().isOk())
                                .andExpect(view().name("dashboard"))
                                .andExpect(model().attributeExists("branches", "selectedBranch", "kpi"));

                mockMvc.perform(get("/dashboard").param("branchId", "1"))
                                .andExpect(status().isOk())
                                .andExpect(view().name("dashboard"))
                                .andExpect(model().attributeExists("recentContracts", "cashFlowSummary"));

                mockMvc.perform(get("/login"))
                                .andExpect(status().isOk())
                                .andExpect(view().name("login"))
                                .andExpect(model().attributeExists("branches"));
        }

        @Test
        @DisplayName("GET /api/v1/dashboard/summary should block non-admin switching to another branch with BRANCH_ACCESS_DENIED (4004)")
        void testDashboardSummaryBlocksUnauthorizedBranchSwitch() {
                UserPrincipal managerBranch1 = UserPrincipal.builder()
                                .id(11L)
                                .username("manager_q1")
                                .fullName("Nguyễn Văn Quản Lý")
                                .role(RoleEnum.MANAGER)
                                .branchId(1L)
                                .branchCode("CN-HCM-01")
                                .branchName("Chi nhánh Quận 1")
                                .active(true)
                                .authorities(Collections.emptyList())
                                .build();

                SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(managerBranch1, null, Collections.emptyList())
                );

                try {
                        BusinessException ex = org.junit.jupiter.api.Assertions.assertThrows(
                                        BusinessException.class,
                                        () -> dashboardController.getDashboardSummary(2L, true)
                        );
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BRANCH_ACCESS_DENIED);
                        assertThat(ex.getErrorCode().getCode()).isEqualTo(4004);
                } finally {
                        SecurityContextHolder.clearContext();
                }
        }

        @Test
        @DisplayName("ADMIN login default (branchId = null) loads system-wide KPIs and monthly contracts across all branches")
        void testAdminDefaultLoadsSystemWideKpiAndMonthlyContracts() {
                UserPrincipal adminPrincipal = UserPrincipal.builder()
                                .id(1L)
                                .username("admin")
                                .fullName("Trần Quản Trị Viên")
                                .role(RoleEnum.ADMIN)
                                .branchId(1L)
                                .branchCode("CN01")
                                .branchName("Chi nhánh Quận 1")
                                .active(true)
                                .authorities(Collections.emptyList())
                                .build();

                SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(adminPrincipal, null, Collections.emptyList())
                );

                try {
                        ContractResponse newNovContractBranch1 = ContractResponse.builder()
                                        .id(101L)
                                        .contractCode("HD-2611-B01-000101")
                                        .branchId(1L)
                                        .branchCode("CN01")
                                        .status(ContractStatus.PENDING_APPROVAL)
                                        .principalAmount(new BigDecimal("30000000"))
                                        .remainingPrincipal(new BigDecimal("30000000"))
                                        .startDate(LocalDate.of(2026, 11, 10))
                                        .nextInterestDueDate(LocalDate.of(2026, 11, 20))
                                        .build();

                        ContractResponse collectibleContractBranch2 = ContractResponse.builder()
                                        .id(201L)
                                        .contractCode("HD-2610-B02-000101")
                                        .branchId(2L)
                                        .branchCode("CN02")
                                        .status(ContractStatus.ACTIVE)
                                        .principalAmount(new BigDecimal("35000000"))
                                        .remainingPrincipal(new BigDecimal("30000000"))
                                        .cycleInterestAmount(new BigDecimal("600000"))
                                        .startDate(LocalDate.of(2026, 10, 25))
                                        .nextInterestDueDate(LocalDate.of(2026, 11, 14))
                                        .build();

                        List<ContractResponse> page0Contracts = Arrays.asList(newNovContractBranch1, collectibleContractBranch2);
                        DashboardContractPageResponse contractPage = DashboardContractPageResponse.of(page0Contracts, 0, 10, 2L, "ALL", null);

                        Map<String, Long> tabCounts = new HashMap<>();
                        tabCounts.put("ALL", 2L);
                        tabCounts.put("PENDING_APPROVAL", 1L);
                        tabCounts.put("ACTIVE", 1L);

                        DashboardSummaryResponse mockSummary = DashboardSummaryResponse.builder()
                                        .selectedBranchId(null)
                                        .selectedBranchCode("ALL")
                                        .selectedBranchName("Toàn hệ thống (Tất cả chi nhánh)")
                                        .canSwitchBranch(true)
                                        .targetMonth("11/2026")
                                        .targetMonthNumber(11)
                                        .currentCashBalance(new BigDecimal("870000000"))
                                        .capitalLimit(new BigDecimal("4500000000"))
                                        .contractPage(contractPage)
                                        .contracts(page0Contracts)
                                        .tabCounts(tabCounts)
                                        .build();

                        when(dashboardService.getDashboardSummary(isNull(), any(), eq(10), eq("2026-11")))
                                        .thenReturn(mockSummary);

                        var apiResponse = dashboardController.getDashboardSummary(null, false, "2026-11");
                        assertThat(apiResponse.isSuccess()).isTrue();

                        var summary = apiResponse.getData();
                        assertThat(summary).isNotNull();
                        assertThat(summary.getSelectedBranchId()).isNull();
                        assertThat(summary.getSelectedBranchCode()).isEqualTo("ALL");
                        assertThat(summary.isCanSwitchBranch()).isTrue();
                        assertThat(summary.getTargetMonth()).isEqualTo("11/2026");
                        assertThat(summary.getCurrentCashBalance()).isEqualByComparingTo("870000000");
                        assertThat(summary.getCapitalLimit()).isEqualByComparingTo("4500000000");

                        // Table contains ONLY new-in-month + collectible-in-month contracts (excludes closed Sep contract)
                        assertThat(summary.getContracts()).hasSize(2);
                        assertThat(summary.getContracts())
                                        .extracting(ContractResponse::getContractCode)
                                        .containsExactly("HD-2611-B01-000101", "HD-2610-B02-000101");
                        assertThat(summary.getTabCounts().get("ALL")).isEqualTo(2L);
                        assertThat(summary.getTabCounts().get("PENDING_APPROVAL")).isEqualTo(1L);
                        assertThat(summary.getTabCounts().get("ACTIVE")).isEqualTo(1L);

                        // Admin login page redirect goes to system-wide /dashboard (not ?branchId=1)
                        assertThat(dashboardController.loginPage()).isEqualTo("redirect:/dashboard");
                } finally {
                        SecurityContextHolder.clearContext();
                }
        }

        @Test
        @DisplayName("ADMIN switching branches dynamically reloads KPIs and monthly contract list per selected branch")
        void testAdminSwitchingBranchesReloadsBranchScopedMetricsAndContracts() {
                UserPrincipal adminPrincipal = UserPrincipal.builder()
                                .id(1L)
                                .username("admin")
                                .fullName("Trần Quản Trị Viên")
                                .role(RoleEnum.ADMIN)
                                .active(true)
                                .authorities(Collections.emptyList())
                                .build();

                SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(adminPrincipal, null, Collections.emptyList())
                );

                try {
                        ContractResponse branch1Contract = ContractResponse.builder()
                                        .id(101L)
                                        .contractCode("HD-2611-B01-000101")
                                        .branchId(1L)
                                        .status(ContractStatus.ACTIVE)
                                        .principalAmount(new BigDecimal("50000000"))
                                        .remainingPrincipal(new BigDecimal("50000000"))
                                        .build();

                        ContractResponse branch2Contract = ContractResponse.builder()
                                        .id(201L)
                                        .contractCode("HD-2611-B02-000101")
                                        .branchId(2L)
                                        .status(ContractStatus.OVERDUE)
                                        .principalAmount(new BigDecimal("20000000"))
                                        .remainingPrincipal(new BigDecimal("20000000"))
                                        .build();

                        DashboardSummaryResponse summaryB1 = DashboardSummaryResponse.builder()
                                        .selectedBranchId(1L)
                                        .currentCashBalance(new BigDecimal("520000000"))
                                        .kpi(DashboardSummaryResponse.KpiSummary.builder().activeContractsCount(1L).overdueContractsCount(0L).build())
                                        .contracts(List.of(branch1Contract))
                                        .build();

                        DashboardSummaryResponse summaryB2 = DashboardSummaryResponse.builder()
                                        .selectedBranchId(2L)
                                        .currentCashBalance(new BigDecimal("350000000"))
                                        .kpi(DashboardSummaryResponse.KpiSummary.builder().activeContractsCount(0L).overdueContractsCount(1L).build())
                                        .contracts(List.of(branch2Contract))
                                        .build();

                        when(dashboardService.getDashboardSummary(eq(1L), any(), eq(10), eq("2026-11"))).thenReturn(summaryB1);
                        when(dashboardService.getDashboardSummary(eq(2L), any(), eq(10), eq("2026-11"))).thenReturn(summaryB2);

                        // Switch to Branch 1
                        var resBranch1 = dashboardController.getDashboardSummary(1L, true, "2026-11").getData();
                        assertThat(resBranch1.getSelectedBranchId()).isEqualTo(1L);
                        assertThat(resBranch1.getCurrentCashBalance()).isEqualByComparingTo("520000000");
                        assertThat(resBranch1.getKpi().getActiveContractsCount()).isEqualTo(1L);
                        assertThat(resBranch1.getKpi().getOverdueContractsCount()).isEqualTo(0L);
                        assertThat(resBranch1.getContracts()).extracting(ContractResponse::getContractCode)
                                        .containsExactly("HD-2611-B01-000101");

                        // Switch to Branch 2
                        var resBranch2 = dashboardController.getDashboardSummary(2L, true, "2026-11").getData();
                        assertThat(resBranch2.getSelectedBranchId()).isEqualTo(2L);
                        assertThat(resBranch2.getCurrentCashBalance()).isEqualByComparingTo("350000000");
                        assertThat(resBranch2.getKpi().getActiveContractsCount()).isEqualTo(0L);
                        assertThat(resBranch2.getKpi().getOverdueContractsCount()).isEqualTo(1L);
                        assertThat(resBranch2.getContracts()).extracting(ContractResponse::getContractCode)
                                        .containsExactly("HD-2611-B02-000101");
                } finally {
                        SecurityContextHolder.clearContext();
                }
        }

        @Test
        @DisplayName("Single-branch user (STAFF/MANAGER) automatically loads only assigned branch data and cannot access ALL")
        void testSingleBranchUserLoadsOnlyAssignedBranch() {
                UserPrincipal staffBranch2 = UserPrincipal.builder()
                                .id(22L)
                                .username("staff_bt")
                                .fullName("Nhân viên Bình Thạnh")
                                .role(RoleEnum.STAFF)
                                .branchId(2L)
                                .branchCode("CN02")
                                .branchName("Chi nhánh Quận Bình Thạnh")
                                .active(true)
                                .authorities(Collections.emptyList())
                                .build();

                SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(staffBranch2, null, Collections.emptyList())
                );

                try {
                        ContractResponse branch2Contract = ContractResponse.builder()
                                        .id(202L)
                                        .contractCode("HD-2611-B02-000102")
                                        .branchId(2L)
                                        .status(ContractStatus.ACTIVE)
                                        .principalAmount(new BigDecimal("30000000"))
                                        .remainingPrincipal(new BigDecimal("30000000"))
                                        .build();

                        DashboardSummaryResponse summaryB2 = DashboardSummaryResponse.builder()
                                        .selectedBranchId(2L)
                                        .canSwitchBranch(false)
                                        .currentCashBalance(new BigDecimal("350000000"))
                                        .contracts(List.of(branch2Contract))
                                        .build();

                        when(dashboardService.getDashboardSummary(eq(2L), any(), eq(10), eq("2026-11"))).thenReturn(summaryB2);

                        // Calling without branchId automatically pins to assigned branchId=2
                        var summary = dashboardController.getDashboardSummary(null, false, "2026-11").getData();
                        assertThat(summary.getSelectedBranchId()).isEqualTo(2L);
                        assertThat(summary.isCanSwitchBranch()).isFalse();
                        assertThat(summary.getCurrentCashBalance()).isEqualByComparingTo("350000000");
                        assertThat(summary.getContracts()).extracting(ContractResponse::getContractCode)
                                        .containsExactly("HD-2611-B02-000102");

                        // Single-branch user login redirect pins ?branchId=2
                        assertThat(dashboardController.loginPage()).isEqualTo("redirect:/dashboard?branchId=2");

                        // Attempting to switch to system-wide ALL (branchId=null, switchAttempt=true) throws 4004
                        BusinessException ex = org.junit.jupiter.api.Assertions.assertThrows(
                                        BusinessException.class,
                                        () -> dashboardController.getDashboardSummary(null, true, "2026-11")
                        );
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BRANCH_ACCESS_DENIED);
                } finally {
                        SecurityContextHolder.clearContext();
                }
        }

        // =========================================================================
        // MILESTONE 4: Dedicated Tests for GET /api/v1/dashboard/contracts
        // =========================================================================

        @Test
        @DisplayName("GET /api/v1/dashboard/contracts should return initial Page 0 with default size=10")
        void testDashboardContractsInitialPage0() throws Exception {
                List<ContractResponse> items = generateMockContracts(10, 1);
                DashboardContractPageResponse page0Response = DashboardContractPageResponse.of(items, 0, 10, 25L, "ALL", null);

                when(dashboardService.getDashboardContractPage(isNull(), eq(DashboardTimeframe.THIS_MONTH), eq(DashboardContractTab.ALL), isNull(), eq(0), eq(10)))
                                .thenReturn(page0Response);

                mockMvc.perform(get("/api/v1/dashboard/contracts"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.page").value(0))
                                .andExpect(jsonPath("$.data.displayPage").value(1))
                                .andExpect(jsonPath("$.data.size").value(10))
                                .andExpect(jsonPath("$.data.totalElements").value(25))
                                .andExpect(jsonPath("$.data.totalPages").value(3))
                                .andExpect(jsonPath("$.data.hasNext").value(true))
                                .andExpect(jsonPath("$.data.hasPrevious").value(false))
                                .andExpect(jsonPath("$.data.items", hasSize(10)))
                                .andExpect(jsonPath("$.data.items[0].contractCode").value("HD-0001"));

                verify(dashboardService).getDashboardContractPage(isNull(), eq(DashboardTimeframe.THIS_MONTH), eq(DashboardContractTab.ALL), isNull(), eq(0), eq(10));
        }

        @Test
        @DisplayName("GET /api/v1/dashboard/contracts should lazily load Page 1 and Page 2 on demand")
        void testDashboardContractsLazyPaginationPage1AndPage2() throws Exception {
                List<ContractResponse> page1Items = generateMockContracts(10, 11);
                DashboardContractPageResponse page1Response = DashboardContractPageResponse.of(page1Items, 1, 10, 25L, "ALL", null);

                List<ContractResponse> page2Items = generateMockContracts(5, 21);
                DashboardContractPageResponse page2Response = DashboardContractPageResponse.of(page2Items, 2, 10, 25L, "ALL", null);

                when(dashboardService.getDashboardContractPage(isNull(), eq(DashboardTimeframe.THIS_MONTH), eq(DashboardContractTab.ALL), isNull(), eq(1), eq(10)))
                                .thenReturn(page1Response);
                when(dashboardService.getDashboardContractPage(isNull(), eq(DashboardTimeframe.THIS_MONTH), eq(DashboardContractTab.ALL), isNull(), eq(2), eq(10)))
                                .thenReturn(page2Response);

                // Fetch Page 1 (Display Page 2)
                mockMvc.perform(get("/api/v1/dashboard/contracts")
                                .param("page", "1")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.page").value(1))
                                .andExpect(jsonPath("$.data.displayPage").value(2))
                                .andExpect(jsonPath("$.data.items", hasSize(10)))
                                .andExpect(jsonPath("$.data.items[0].contractCode").value("HD-0011"))
                                .andExpect(jsonPath("$.data.hasNext").value(true))
                                .andExpect(jsonPath("$.data.hasPrevious").value(true));

                // Fetch Page 2 (Display Page 3 - Last Page)
                mockMvc.perform(get("/api/v1/dashboard/contracts")
                                .param("page", "2")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.page").value(2))
                                .andExpect(jsonPath("$.data.displayPage").value(3))
                                .andExpect(jsonPath("$.data.items", hasSize(5)))
                                .andExpect(jsonPath("$.data.items[0].contractCode").value("HD-0021"))
                                .andExpect(jsonPath("$.data.hasNext").value(false))
                                .andExpect(jsonPath("$.data.hasPrevious").value(true));

                verify(dashboardService).getDashboardContractPage(isNull(), eq(DashboardTimeframe.THIS_MONTH), eq(DashboardContractTab.ALL), isNull(), eq(1), eq(10));
                verify(dashboardService).getDashboardContractPage(isNull(), eq(DashboardTimeframe.THIS_MONTH), eq(DashboardContractTab.ALL), isNull(), eq(2), eq(10));
        }

        @Test
        @DisplayName("GET /api/v1/dashboard/contracts should block unauthorized branch access with 403 and code 4004")
        void testDashboardContractsBlocksUnauthorizedBranchAccess() {
                UserPrincipal managerBranch1 = UserPrincipal.builder()
                                .id(11L)
                                .username("manager_q1")
                                .fullName("Nguyễn Văn Quản Lý")
                                .role(RoleEnum.MANAGER)
                                .branchId(1L)
                                .branchCode("CN-HCM-01")
                                .branchName("Chi nhánh Quận 1")
                                .active(true)
                                .authorities(Collections.emptyList())
                                .build();

                SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(managerBranch1, null, Collections.emptyList())
                );

                try {
                        BusinessException ex = org.junit.jupiter.api.Assertions.assertThrows(
                                        BusinessException.class,
                                        () -> dashboardController.getDashboardContracts(2L, "THIS_MONTH", "ALL", null, 0, 10)
                        );
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BRANCH_ACCESS_DENIED);
                        assertThat(ex.getErrorCode().getCode()).isEqualTo(4004);
                        assertThat(ex.getMessage()).contains("Truy cập bị từ chối");
                        verify(dashboardService, never()).getDashboardContractPage(any(), any(), any(), any(), anyInt(), anyInt());
                } finally {
                        SecurityContextHolder.clearContext();
                }
        }

        @Test
        @DisplayName("MockMvc GET /api/v1/dashboard/contracts should return 403 Forbidden with code 4004 when unauthorized branch is requested")
        void testMockMvcDashboardContractsBlocksUnauthorizedBranch() throws Exception {
                UserPrincipal managerBranch1 = UserPrincipal.builder()
                                .id(11L)
                                .username("manager_q1")
                                .role(RoleEnum.MANAGER)
                                .branchId(1L)
                                .active(true)
                                .authorities(Collections.emptyList())
                                .build();

                SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(managerBranch1, null, Collections.emptyList())
                );

                try {
                        mockMvc.perform(get("/api/v1/dashboard/contracts")
                                        .param("branchId", "2")
                                        .param("timeframe", "THIS_MONTH")
                                        .param("page", "0")
                                        .param("size", "10"))
                                        .andExpect(status().isForbidden())
                                        .andExpect(jsonPath("$.success").value(false))
                                        .andExpect(jsonPath("$.code").value(4004))
                                        .andExpect(jsonPath("$.message").value(containsString("Truy cập bị từ chối")));

                        verify(dashboardService, never()).getDashboardContractPage(any(), any(), any(), any(), anyInt(), anyInt());
                } finally {
                        SecurityContextHolder.clearContext();
                }
        }

        @Test
        @DisplayName("GET /api/v1/dashboard/contracts for single-branch user with branchId=null automatically defaults to assigned branch")
        void testDashboardContractsDefaultsToAssignedBranchForStaff() {
                UserPrincipal staffBranch2 = UserPrincipal.builder()
                                .id(22L)
                                .username("staff_bt")
                                .role(RoleEnum.STAFF)
                                .branchId(2L)
                                .active(true)
                                .authorities(Collections.emptyList())
                                .build();

                SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(staffBranch2, null, Collections.emptyList())
                );

                try {
                        DashboardContractPageResponse mockPage = DashboardContractPageResponse.empty(10, "ALL", null);
                        when(dashboardService.getDashboardContractPage(eq(2L), any(), any(), any(), eq(0), eq(10)))
                                        .thenReturn(mockPage);

                        var response = dashboardController.getDashboardContracts(null, "THIS_MONTH", "ALL", null, 0, 10);
                        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                        assertThat(response.getBody()).isNotNull();
                        assertThat(response.getBody().isSuccess()).isTrue();
                        verify(dashboardService).getDashboardContractPage(eq(2L), eq(DashboardTimeframe.THIS_MONTH), eq(DashboardContractTab.ALL), isNull(), eq(0), eq(10));
                } finally {
                        SecurityContextHolder.clearContext();
                }
        }

        @Test
        @DisplayName("GET /api/v1/dashboard/contracts for ADMIN permits system-wide (null) and explicit branches")
        void testDashboardContractsAllowsAdminSystemWideAndExplicitBranch() {
                UserPrincipal admin = UserPrincipal.builder()
                                .id(1L)
                                .username("admin")
                                .role(RoleEnum.ADMIN)
                                .active(true)
                                .authorities(Collections.emptyList())
                                .build();

                SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(admin, null, Collections.emptyList())
                );

                try {
                        DashboardContractPageResponse mockPage = DashboardContractPageResponse.empty(10, "ALL", null);
                        when(dashboardService.getDashboardContractPage(isNull(), any(), any(), any(), eq(0), eq(10)))
                                        .thenReturn(mockPage);
                        when(dashboardService.getDashboardContractPage(eq(2L), any(), any(), any(), eq(1), eq(10)))
                                        .thenReturn(mockPage);

                        // System-wide
                        var resAll = dashboardController.getDashboardContracts(null, "THIS_MONTH", "ALL", null, 0, 10);
                        assertThat(resAll.getStatusCode().is2xxSuccessful()).isTrue();
                        verify(dashboardService).getDashboardContractPage(isNull(), eq(DashboardTimeframe.THIS_MONTH), eq(DashboardContractTab.ALL), isNull(), eq(0), eq(10));

                        // Explicit branch
                        var resB2 = dashboardController.getDashboardContracts(2L, "THIS_MONTH", "ALL", null, 1, 10);
                        assertThat(resB2.getStatusCode().is2xxSuccessful()).isTrue();
                        verify(dashboardService).getDashboardContractPage(eq(2L), eq(DashboardTimeframe.THIS_MONTH), eq(DashboardContractTab.ALL), isNull(), eq(1), eq(10));
                } finally {
                        SecurityContextHolder.clearContext();
                }
        }

        @Test
        @DisplayName("GET /api/v1/dashboard/contracts with status tab and keyword filter passes correctly to service")
        void testDashboardContractsWithStatusTabAndSearchKeyword() {
                DashboardContractPageResponse mockPage = DashboardContractPageResponse.empty(10, "ACTIVE", "HD-2611");
                when(dashboardService.getDashboardContractPage(eq(1L), eq(DashboardTimeframe.LAST_7_DAYS), eq(DashboardContractTab.ACTIVE), eq("HD-2611"), eq(0), eq(10)))
                                .thenReturn(mockPage);

                var response = dashboardController.getDashboardContracts(1L, "LAST_7_DAYS", "ACTIVE", "HD-2611", 0, 10);
                assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                assertThat(response.getBody().getData().getActiveTab()).isEqualTo("ACTIVE");
                assertThat(response.getBody().getData().getKeyword()).isEqualTo("HD-2611");

                verify(dashboardService).getDashboardContractPage(eq(1L), eq(DashboardTimeframe.LAST_7_DAYS), eq(DashboardContractTab.ACTIVE), eq("HD-2611"), eq(0), eq(10));
        }
}

package com.pawnmaster.api.modules.dashboard;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

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

        @InjectMocks
        private DashboardController dashboardController;

        private MockMvc mockMvc;

        private BranchResponse branch1;
        private BranchResponse branch2;

        @BeforeEach
        void setUp() {
                InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
                viewResolver.setPrefix("/templates/");
                viewResolver.setSuffix(".html");

                mockMvc = MockMvcBuilders.standaloneSetup(dashboardController)
                                .setViewResolvers(viewResolver)
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
        }

        @Test
        @DisplayName("GET /dashboard without branchId should select first branch and populate all model attributes")
        void testDashboardDefaultBranch() {
                when(branchService.getActiveBranches()).thenReturn(Arrays.asList(branch1, branch2));
                when(contractService.getContractsByBranch(1L)).thenReturn(Collections.emptyList());
                when(cashbookService.getBranchCashFlowSummary(1L)).thenReturn(CashFlowSummaryResponse.builder()
                                .branchId(1L)
                                .branchName("Chi nhánh Quận 1")
                                .currentCashBalance(new BigDecimal("520000000"))
                                .build());

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
                when(branchService.getActiveBranches()).thenReturn(Arrays.asList(branch1, branch2));
                when(contractService.getContractsByBranch(2L)).thenReturn(Collections.emptyList());
                when(cashbookService.getBranchCashFlowSummary(2L)).thenReturn(CashFlowSummaryResponse.builder()
                                .branchId(2L)
                                .branchName("Chi nhánh Quận Bình Thạnh")
                                .currentCashBalance(new BigDecimal("350000000"))
                                .build());

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

                when(branchService.getActiveBranches()).thenReturn(Arrays.asList(branch1));
                when(branchService.getBranchById(99L)).thenReturn(branch99);
                when(contractService.getContractsByBranch(99L)).thenReturn(Collections.emptyList());

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
                when(contractService.getAllContracts()).thenReturn(Collections.emptyList());

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
                when(contractService.getAllContracts()).thenThrow(new RuntimeException("Contract error"));

                Model model = new ConcurrentModel();
                String view = dashboardController.dashboard(null, model);

                assertThat(view).isEqualTo("dashboard");
                assertThat(model.getAttribute("selectedBranch")).isNull();
                assertThat(model.getAttribute("recentContracts")).isEqualTo(Collections.emptyList());
        }

        @Test
        @DisplayName("KPI calculation should aggregate active, overdue, and pending approval metrics")
        void testKpiCalculation() {
                ContractResponse c1 = ContractResponse.builder()
                                .id(1L)
                                .status(ContractStatus.ACTIVE)
                                .principalAmount(new BigDecimal("50000000"))
                                .remainingPrincipal(new BigDecimal("50000000"))
                                .totalInterestPaid(new BigDecimal("3000000"))
                                .build();

                ContractResponse c2 = ContractResponse.builder()
                                .id(2L)
                                .status(ContractStatus.OVERDUE)
                                .principalAmount(new BigDecimal("20000000"))
                                .remainingPrincipal(new BigDecimal("20000000"))
                                .totalInterestPaid(new BigDecimal("500000"))
                                .build();

                ContractResponse c3 = ContractResponse.builder()
                                .id(3L)
                                .status(ContractStatus.PENDING_APPROVAL)
                                .principalAmount(new BigDecimal("30000000"))
                                .remainingPrincipal(new BigDecimal("30000000"))
                                .totalInterestPaid(BigDecimal.ZERO)
                                .build();

                when(branchService.getActiveBranches()).thenReturn(Arrays.asList(branch1));
                when(contractService.getContractsByBranch(1L)).thenReturn(Arrays.asList(c1, c2, c3));

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
                when(contractService.getContractsByBranch(1L)).thenReturn(Collections.emptyList());

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
}

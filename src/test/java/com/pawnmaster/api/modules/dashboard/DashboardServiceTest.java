package com.pawnmaster.api.modules.dashboard;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.branch.BranchService;
import com.pawnmaster.api.modules.branch.dto.BranchResponse;
import com.pawnmaster.api.modules.cashbook.CashbookService;
import com.pawnmaster.api.modules.cashbook.TransactionType;
import com.pawnmaster.api.modules.cashbook.dto.CashFlowSummaryResponse;
import com.pawnmaster.api.modules.cashbook.dto.CashbookResponse;
import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.contract.ContractService;
import com.pawnmaster.api.modules.contract.ContractStatus;
import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import com.pawnmaster.api.modules.contract.dto.DashboardKpiProjection;
import com.pawnmaster.api.modules.dashboard.dto.DashboardContractPageResponse;
import com.pawnmaster.api.modules.dashboard.dto.DashboardSummaryResponse;
import com.pawnmaster.api.modules.user.RoleEnum;
import com.pawnmaster.api.security.BranchSecurityGuard;
import com.pawnmaster.api.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Milestone 3: DashboardService & DashboardServiceImpl Unit Tests")
class DashboardServiceTest {

        @Mock
        private BranchService branchService;

        @Mock
        private ContractService contractService;

        @Mock
        private CashbookService cashbookService;

        @Mock
        private BranchSecurityGuard branchSecurityGuard;

        @InjectMocks
        private DashboardServiceImpl dashboardService;

        private BranchResponse branch1;
        private BranchResponse branch2;

        @BeforeEach
        void setUp() {
                branch1 = BranchResponse.builder()
                                .id(1L)
                                .branchCode("CN01")
                                .branchName("Chi nhánh Quận 1")
                                .capitalLimit(new BigDecimal("2500000000"))
                                .currentCashBalance(new BigDecimal("500000000"))
                                .active(true)
                                .build();

                branch2 = BranchResponse.builder()
                                .id(2L)
                                .branchCode("CN02")
                                .branchName("Chi nhánh Bình Thạnh")
                                .capitalLimit(new BigDecimal("1500000000"))
                                .currentCashBalance(new BigDecimal("300000000"))
                                .active(true)
                                .build();
        }

        private List<ContractResponse> generateMockContracts(int count) {
                List<ContractResponse> contracts = new ArrayList<>(count);
                for (int i = 1; i <= count; i++) {
                        contracts.add(ContractResponse.builder()
                                        .id((long) i)
                                        .contractCode(String.format("HD-%04d", i))
                                        .customerName("Khách hàng " + i)
                                        .customerPhone("090123456" + (i % 10))
                                        .principalAmount(new BigDecimal("10000000"))
                                        .remainingPrincipal(new BigDecimal("10000000"))
                                        .status(ContractStatus.ACTIVE)
                                        .collateralCategory(CollateralCategory.MOTORBIKE)
                                        .collateralAppraisedValue(new BigDecimal("15000000"))
                                        .build());
                }
                return contracts;
        }

        private void stubStandardSummaryDependencies(Long branchId) {
                lenient().when(branchSecurityGuard.resolveAuthorizedBranchId(any()))
                                .thenAnswer(inv -> inv.getArgument(0));
                lenient().when(branchService.getActiveBranches()).thenReturn(List.of(branch1, branch2));
                lenient().when(cashbookService.getBranchCashFlowSummary(any())).thenReturn(
                                CashFlowSummaryResponse.builder()
                                                .branchId(branchId)
                                                .currentCashBalance(new BigDecimal("500000000"))
                                                .totalCashIn(new BigDecimal("120000000"))
                                                .totalCashOut(new BigDecimal("80000000"))
                                                .build());

                DashboardKpiProjection kpiProj = mock(DashboardKpiProjection.class);
                lenient().when(kpiProj.getActiveCount()).thenReturn(20L);
                lenient().when(kpiProj.getPendingCount()).thenReturn(3L);
                lenient().when(kpiProj.getDisbursedPrincipal()).thenReturn(new BigDecimal("500000000"));
                lenient().when(kpiProj.getOverdueCount()).thenReturn(2L);
                lenient().when(kpiProj.getWaitingLiquidationCount()).thenReturn(1L);
                lenient().when(kpiProj.getAtRiskPrincipal()).thenReturn(new BigDecimal("50000000"));
                lenient().when(contractService.getPortfolioKpi(any())).thenReturn(kpiProj);

                Map<String, Long> tabCounts = new LinkedHashMap<>();
                tabCounts.put("ALL", 25L);
                tabCounts.put("PENDING_APPROVAL", 3L);
                tabCounts.put("ACTIVE", 20L);
                tabCounts.put("DUE_TODAY", 1L);
                tabCounts.put("OVERDUE", 2L);
                tabCounts.put("WAITING_LIQUIDATION", 1L);
                lenient().when(contractService.getDashboardTabCounts(any(), any())).thenReturn(tabCounts);

                lenient().when(contractService.getCollectedInterestInPeriod(any(), any(), any()))
                                .thenReturn(new BigDecimal("25000000"));
                lenient().when(contractService.getUpcomingCycleInterest(any(), any()))
                                .thenReturn(new BigDecimal("75000000"));
                lenient().when(contractService.getDashboardCollateralDistribution(any()))
                                .thenReturn(Collections.emptyList());
                lenient().when(cashbookService.getTransactionsByScopeAndPeriod(any(), any(), any()))
                                .thenReturn(Collections.emptyList());
        }

        @Nested
        @DisplayName("1. Page 0 Isolation on Summary Load")
        class Page0IsolationTests {

                @Test
                @DisplayName("Should strictly load Page 0 (10 items) when 25 contracts exist, with totalElements = 25 and totalPages = 3")
                void shouldReturnStrictlyPage0When25ContractsExistInPeriod() {
                        Long branchId = 1L;
                        stubStandardSummaryDependencies(branchId);

                        List<ContractResponse> all25 = generateMockContracts(25);
                        List<ContractResponse> page0Items = all25.subList(0, 10);
                        Page<ContractResponse> mockPage = new PageImpl<>(page0Items, PageRequest.of(0, 10), 25);

                        when(contractService.findDashboardContractPage(
                                        eq(branchId),
                                        any(DashboardPeriod.class),
                                        eq(DashboardContractTab.ALL),
                                        eq(null),
                                        eq(PageRequest.of(0, 10)))).thenReturn(mockPage);

                        DashboardSummaryResponse summary = dashboardService.getDashboardSummary(
                                        branchId, DashboardTimeframe.THIS_MONTH, 10, (Integer) null);

                        assertThat(summary).isNotNull();
                        // Verify Page 0 contractPage DTO
                        DashboardContractPageResponse page = summary.getContractPage();
                        assertThat(page).isNotNull();
                        assertThat(page.getItems()).hasSize(10);
                        assertThat(page.getPage()).isEqualTo(0);
                        assertThat(page.getDisplayPage()).isEqualTo(1);
                        assertThat(page.getSize()).isEqualTo(10);
                        assertThat(page.getTotalElements()).isEqualTo(25L);
                        assertThat(page.getTotalPages()).isEqualTo(3);
                        assertThat(page.isHasNext()).isTrue();
                        assertThat(page.isHasPrevious()).isFalse();

                        // Verify backward-compatible contracts list has exactly the 10 Page 0 items
                        assertThat(summary.getContracts()).hasSize(10);
                        assertThat(summary.getContracts().get(0).getContractCode()).isEqualTo("HD-0001");
                        assertThat(summary.getContracts().get(9).getContractCode()).isEqualTo("HD-0010");

                        // Verify full list loaders are NEVER called
                        verify(contractService, never()).getAllContracts();
                        verify(contractService, never()).getContractsByBranch(anyLong());
                }

                @Test
                @DisplayName("Should handle single-page result when total contracts <= pageSize (5 contracts, size = 10)")
                void shouldHandleSinglePageWhenContractsLessThanPageSize() {
                        Long branchId = 1L;
                        stubStandardSummaryDependencies(branchId);

                        List<ContractResponse> fiveContracts = generateMockContracts(5);
                        Page<ContractResponse> mockPage = new PageImpl<>(fiveContracts, PageRequest.of(0, 10), 5);

                        when(contractService.findDashboardContractPage(
                                        eq(branchId), any(), eq(DashboardContractTab.ALL), eq(null),
                                        eq(PageRequest.of(0, 10)))).thenReturn(mockPage);

                        DashboardSummaryResponse summary = dashboardService.getDashboardSummary(branchId);

                        assertThat(summary.getContractPage().getItems()).hasSize(5);
                        assertThat(summary.getContractPage().getTotalElements()).isEqualTo(5L);
                        assertThat(summary.getContractPage().getTotalPages()).isEqualTo(1);
                        assertThat(summary.getContractPage().isHasNext()).isFalse();
                        assertThat(summary.getContractPage().isHasPrevious()).isFalse();
                        assertThat(summary.getContracts()).hasSize(5);
                }

                @Test
                @DisplayName("Should return empty page when no contracts match the period (totalElements = 0, totalPages = 0)")
                void shouldReturnEmptyPageWhenNoContractsFound() {
                        Long branchId = 1L;
                        stubStandardSummaryDependencies(branchId);

                        Page<ContractResponse> emptyPage = new PageImpl<>(Collections.emptyList(),
                                        PageRequest.of(0, 10), 0);
                        when(contractService.findDashboardContractPage(
                                        eq(branchId), any(), eq(DashboardContractTab.ALL), eq(null),
                                        eq(PageRequest.of(0, 10)))).thenReturn(emptyPage);

                        DashboardSummaryResponse summary = dashboardService.getDashboardSummary(branchId);

                        assertThat(summary.getContractPage().getItems()).isEmpty();
                        assertThat(summary.getContractPage().getTotalElements()).isEqualTo(0L);
                        assertThat(summary.getContractPage().getTotalPages()).isEqualTo(0);
                        assertThat(summary.getContractPage().isHasNext()).isFalse();
                        assertThat(summary.getContractPage().isHasPrevious()).isFalse();
                        assertThat(summary.getContracts()).isEmpty();
                }

                @Test
                @DisplayName("Should clamp rawSize on summary load: size 0 defaults to 10, size 100 clamped to 50")
                void shouldClampPageSizeOnSummaryLoad() {
                        Long branchId = 1L;
                        stubStandardSummaryDependencies(branchId);

                        Page<ContractResponse> emptyPage = new PageImpl<>(Collections.emptyList(),
                                        PageRequest.of(0, 10), 0);
                        when(contractService.findDashboardContractPage(
                                        eq(branchId), any(), eq(DashboardContractTab.ALL), eq(null),
                                        any(Pageable.class))).thenReturn(emptyPage);

                        // size = -5 -> clamped to 10
                        dashboardService.getDashboardSummary(branchId, DashboardTimeframe.THIS_MONTH, -5);
                        verify(contractService).findDashboardContractPage(
                                        eq(branchId), any(), eq(DashboardContractTab.ALL), eq(null),
                                        eq(PageRequest.of(0, 10)));

                        // size = 100 -> clamped to 50
                        dashboardService.getDashboardSummary(branchId, DashboardTimeframe.THIS_MONTH, 100);
                        verify(contractService).findDashboardContractPage(
                                        eq(branchId), any(), eq(DashboardContractTab.ALL), eq(null),
                                        eq(PageRequest.of(0, 50)));
                }
        }

        @Nested
        @DisplayName("2. Standalone Lazy Contract Pagination (getDashboardContractPage)")
        class LazyPaginationTests {

                @Test
                @DisplayName("Should lazily fetch Page 1 (items 11-20) and Page 2 (items 21-25) without touching KPIs or charts")
                void shouldLazilyFetchSubsequentPagesWithoutRecomputingKpisOrCharts() {
                        Long branchId = 1L;
                        when(branchSecurityGuard.resolveAuthorizedBranchId(branchId)).thenReturn(branchId);

                        List<ContractResponse> all25 = generateMockContracts(25);
                        List<ContractResponse> page1Items = all25.subList(10, 20);
                        List<ContractResponse> page2Items = all25.subList(20, 25);

                        Page<ContractResponse> mockPage1 = new PageImpl<>(page1Items, PageRequest.of(1, 10), 25);
                        Page<ContractResponse> mockPage2 = new PageImpl<>(page2Items, PageRequest.of(2, 10), 25);

                        when(contractService.findDashboardContractPage(
                                        eq(branchId), any(), eq(DashboardContractTab.ALL), eq(null),
                                        eq(PageRequest.of(1, 10)))).thenReturn(mockPage1);

                        when(contractService.findDashboardContractPage(
                                        eq(branchId), any(), eq(DashboardContractTab.ALL), eq(null),
                                        eq(PageRequest.of(2, 10)))).thenReturn(mockPage2);

                        // Fetch Page 1
                        DashboardContractPageResponse responsePage1 = dashboardService.getDashboardContractPage(
                                        branchId, DashboardTimeframe.THIS_MONTH, DashboardContractTab.ALL, null, 1, 10);

                        assertThat(responsePage1.getItems()).hasSize(10);
                        assertThat(responsePage1.getPage()).isEqualTo(1);
                        assertThat(responsePage1.getDisplayPage()).isEqualTo(2);
                        assertThat(responsePage1.isHasNext()).isTrue();
                        assertThat(responsePage1.isHasPrevious()).isTrue();
                        assertThat(responsePage1.getItems().get(0).getContractCode()).isEqualTo("HD-0011");

                        // Fetch Page 2
                        DashboardContractPageResponse responsePage2 = dashboardService.getDashboardContractPage(
                                        branchId, DashboardTimeframe.THIS_MONTH, DashboardContractTab.ALL, null, 2, 10);

                        assertThat(responsePage2.getItems()).hasSize(5);
                        assertThat(responsePage2.getPage()).isEqualTo(2);
                        assertThat(responsePage2.getDisplayPage()).isEqualTo(3);
                        assertThat(responsePage2.isHasNext()).isFalse();
                        assertThat(responsePage2.isHasPrevious()).isTrue();
                        assertThat(responsePage2.getItems().get(0).getContractCode()).isEqualTo("HD-0021");

                        // Verify KPI / Chart / Cashbook services were NEVER called during lazy page
                        // fetch
                        verify(cashbookService, never()).getBranchCashFlowSummary(any());
                        verify(cashbookService, never()).getTransactionsByScopeAndPeriod(any(), any(), any());
                        verify(contractService, never()).getPortfolioKpi(any());
                        verify(contractService, never()).getPeriodKpi(any(), any());
                        verify(contractService, never()).getDashboardTabCounts(any(), any());
                        verify(contractService, never()).getDashboardCollateralDistribution(any());
                }

                @Test
                @DisplayName("Should clamp negative page to 0 and clamp size between 1 and 50")
                void shouldClampPageAndSizeParameters() {
                        Long branchId = 1L;
                        when(branchSecurityGuard.resolveAuthorizedBranchId(branchId)).thenReturn(branchId);

                        Page<ContractResponse> emptyPage = new PageImpl<>(Collections.emptyList(),
                                        PageRequest.of(0, 10), 0);
                        when(contractService.findDashboardContractPage(any(), any(), any(), any(), any(Pageable.class)))
                                        .thenReturn(emptyPage);

                        // page = -5 -> clamped to 0, size = 0 -> normalized to 10
                        dashboardService.getDashboardContractPage(branchId, DashboardTimeframe.THIS_MONTH,
                                        DashboardContractTab.ALL, null, -5, 0);
                        verify(contractService).findDashboardContractPage(
                                        eq(branchId), any(), eq(DashboardContractTab.ALL), eq(null),
                                        eq(PageRequest.of(0, 10)));

                        // size = 5000 -> clamped to 50
                        dashboardService.getDashboardContractPage(branchId, DashboardTimeframe.THIS_MONTH,
                                        DashboardContractTab.ALL, null, 2, 5000);
                        verify(contractService).findDashboardContractPage(
                                        eq(branchId), any(), eq(DashboardContractTab.ALL), eq(null),
                                        eq(PageRequest.of(2, 50)));
                }
        }

        @Nested
        @DisplayName("3. Tab Filtering & Search Keyword Delegation")
        class FilteringAndSearchTests {

                @Test
                @DisplayName("Should delegate activeTab and trim search keyword properly to ContractService")
                void shouldDelegateTabAndKeywordProperly() {
                        Long branchId = 1L;
                        when(branchSecurityGuard.resolveAuthorizedBranchId(branchId)).thenReturn(branchId);

                        Page<ContractResponse> singleResult = new PageImpl<>(
                                        List.of(ContractResponse.builder().id(1L).contractCode("HD-0001").build()),
                                        PageRequest.of(0, 10), 1);

                        when(contractService.findDashboardContractPage(
                                        eq(branchId),
                                        any(DashboardPeriod.class),
                                        eq(DashboardContractTab.ACTIVE),
                                        eq("HD-0001"),
                                        eq(PageRequest.of(0, 10)))).thenReturn(singleResult);

                        DashboardContractPageResponse response = dashboardService.getDashboardContractPage(
                                        branchId,
                                        DashboardTimeframe.THIS_MONTH,
                                        DashboardContractTab.ACTIVE,
                                        "  HD-0001  ",
                                        0,
                                        10);

                        assertThat(response.getItems()).hasSize(1);
                        assertThat(response.getActiveTab()).isEqualTo("ACTIVE");
                        assertThat(response.getKeyword()).isEqualTo("HD-0001");
                }

                @Test
                @DisplayName("Should treat whitespace-only keyword as null and null tab as ALL")
                void shouldTreatWhitespaceKeywordAsNullAndNullTabAsAll() {
                        Long branchId = 1L;
                        when(branchSecurityGuard.resolveAuthorizedBranchId(branchId)).thenReturn(branchId);

                        Page<ContractResponse> emptyPage = new PageImpl<>(Collections.emptyList(),
                                        PageRequest.of(0, 10), 0);
                        when(contractService.findDashboardContractPage(
                                        eq(branchId),
                                        any(DashboardPeriod.class),
                                        eq(DashboardContractTab.ALL),
                                        isNull(),
                                        eq(PageRequest.of(0, 10)))).thenReturn(emptyPage);

                        DashboardContractPageResponse response = dashboardService.getDashboardContractPage(
                                        branchId,
                                        null,
                                        null,
                                        "     ",
                                        0,
                                        10);

                        assertThat(response.getActiveTab()).isEqualTo("ALL");
                        assertThat(response.getKeyword()).isNull();
                }
        }

        @Nested
        @DisplayName("4. Timeframe & Period Resolution")
        class TimeframeResolutionTests {

                @Test
                @DisplayName("Should resolve LAST_7_DAYS timeframe and compute corresponding period")
                void shouldResolveLast7DaysTimeframe() {
                        Long branchId = 1L;
                        stubStandardSummaryDependencies(branchId);

                        Page<ContractResponse> emptyPage = new PageImpl<>(Collections.emptyList(),
                                        PageRequest.of(0, 10), 0);
                        ArgumentCaptor<DashboardPeriod> periodCaptor = ArgumentCaptor.forClass(DashboardPeriod.class);

                        when(contractService.findDashboardContractPage(
                                        eq(branchId),
                                        periodCaptor.capture(),
                                        eq(DashboardContractTab.ALL),
                                        isNull(),
                                        any())).thenReturn(emptyPage);

                        DashboardSummaryResponse summary = dashboardService.getDashboardSummary(
                                        branchId, DashboardTimeframe.LAST_7_DAYS, 10, (Integer) null);

                        assertThat(summary.getSelectedTimeframe()).isEqualTo(DashboardTimeframe.LAST_7_DAYS);
                        assertThat(periodCaptor.getValue().timeframe()).isEqualTo(DashboardTimeframe.LAST_7_DAYS);
                        assertThat(summary.getPeriodStartDate()).isEqualTo(LocalDate.now().minusDays(6));
                        assertThat(summary.getPeriodEndDate()).isEqualTo(LocalDate.now());
                }

                @Test
                @DisplayName("Should support legacy month integer (e.g. month = 5) and string formats (e.g. '2026-05')")
                void shouldSupportLegacyMonthParameter() {
                        Long branchId = 1L;
                        stubStandardSummaryDependencies(branchId);

                        Page<ContractResponse> emptyPage = new PageImpl<>(Collections.emptyList(),
                                        PageRequest.of(0, 10), 0);
                        when(contractService.findDashboardContractPage(any(), any(), any(), any(), any()))
                                        .thenReturn(emptyPage);

                        DashboardSummaryResponse summaryInt = dashboardService.getDashboardSummary(
                                        branchId, DashboardTimeframe.THIS_MONTH, 10, 5);
                        assertThat(summaryInt.getTargetMonthNumber()).isEqualTo(5);
                        assertThat(summaryInt.getTargetMonth()).contains("/" + LocalDate.now().getYear());

                        DashboardSummaryResponse summaryStr = dashboardService.getDashboardSummary(
                                        branchId, DashboardTimeframe.THIS_MONTH, 10, "2026-05");
                        assertThat(summaryStr.getTargetMonthNumber()).isEqualTo(5);
                        assertThat(summaryStr.getTargetMonth()).isEqualTo("05/2026");
                }
        }

        @Nested
        @DisplayName("5. Cash Flow Chart Series Datasets")
        class CashFlowChartTests {

                @Test
                @DisplayName("Should generate chart series for all 5 timeframes + 3 legacy aliases (days, months, year)")
                void shouldGenerateAll5TimeframesAndLegacyAliases() {
                        Long branchId = 1L;
                        stubStandardSummaryDependencies(branchId);

                        LocalDate today = LocalDate.now();
                        LocalDateTime txTime = today.atTime(10, 30);

                        CashbookResponse outTx = CashbookResponse.builder()
                                        .id(1L)
                                        .transactionDate(txTime)
                                        .transactionType(TransactionType.CASH_OUT)
                                        .amount(new BigDecimal("15000000")) // 15 million
                                        .build();

                        CashbookResponse inTx = CashbookResponse.builder()
                                        .id(2L)
                                        .transactionDate(txTime)
                                        .transactionType(TransactionType.CASH_IN)
                                        .amount(new BigDecimal("10000000")) // 10 million
                                        .build();

                        when(cashbookService.getTransactionsByScopeAndPeriod(eq(branchId), any(), any()))
                                        .thenReturn(List.of(outTx, inTx));

                        Page<ContractResponse> emptyPage = new PageImpl<>(Collections.emptyList(),
                                        PageRequest.of(0, 10), 0);
                        when(contractService.findDashboardContractPage(any(), any(), any(), any(), any()))
                                        .thenReturn(emptyPage);

                        DashboardSummaryResponse summary = dashboardService.getDashboardSummary(branchId);

                        Map<String, DashboardSummaryResponse.CashFlowChartSeries> charts = summary.getCashFlowCharts();
                        assertThat(charts).isNotNull();

                        // All 5 timeframe keys must exist
                        assertThat(charts).containsKeys("LAST_7_DAYS", "THIS_MONTH", "LAST_MONTH", "LAST_6_MONTHS",
                                        "THIS_YEAR");

                        // Legacy alias keys must also exist
                        assertThat(charts).containsKeys("days", "months", "year");

                        // Aliases must match the respective series
                        assertThat(charts.get("days")).isEqualTo(charts.get("LAST_7_DAYS"));
                        assertThat(charts.get("months")).isEqualTo(charts.get("LAST_6_MONTHS"));
                        assertThat(charts.get("year")).isEqualTo(charts.get("THIS_YEAR"));

                        // Check bucket counts
                        assertThat(charts.get("LAST_7_DAYS").getLabels()).hasSize(7);
                        assertThat(charts.get("LAST_6_MONTHS").getLabels()).hasSize(6);
                        assertThat(charts.get("THIS_YEAR").getLabels()).hasSize(12);

                        // Invariant: labels.size == disburseMillions.size == collectMillions.size
                        for (Map.Entry<String, DashboardSummaryResponse.CashFlowChartSeries> entry : charts
                                        .entrySet()) {
                                DashboardSummaryResponse.CashFlowChartSeries s = entry.getValue();
                                assertThat(s.getLabels()).hasSameSizeAs(s.getDisburseMillions());
                                assertThat(s.getLabels()).hasSameSizeAs(s.getCollectMillions());
                        }

                        // Verify amount conversion (15M and 10M on today's bucket)
                        DashboardSummaryResponse.CashFlowChartSeries daySeries = charts.get("LAST_7_DAYS");
                        int lastIndex = daySeries.getLabels().size() - 1; // today is last bucket of LAST_7_DAYS
                        assertThat(daySeries.getDisburseMillions().get(lastIndex))
                                        .isEqualByComparingTo(new BigDecimal("15.00"));
                        assertThat(daySeries.getCollectMillions().get(lastIndex))
                                        .isEqualByComparingTo(new BigDecimal("10.00"));
                }
        }

        @Nested
        @DisplayName("6. KPI Calculation & Safe Division")
        class KpiCalculationTests {

                @Test
                @DisplayName("Should accurately calculate capital utilization and interest collection percentages")
                void shouldCalculatePercentagesAccurately() {
                        Long branchId = 1L;
                        stubStandardSummaryDependencies(branchId);

                        DashboardKpiProjection kpiProj = mock(DashboardKpiProjection.class);
                        when(kpiProj.getActiveCount()).thenReturn(10L);
                        when(kpiProj.getPendingCount()).thenReturn(2L);
                        when(kpiProj.getDisbursedPrincipal()).thenReturn(new BigDecimal("500000000")); // 500M
                        when(kpiProj.getOverdueCount()).thenReturn(3L);
                        when(kpiProj.getWaitingLiquidationCount()).thenReturn(1L);
                        when(kpiProj.getAtRiskPrincipal()).thenReturn(new BigDecimal("60000000"));
                        when(contractService.getPortfolioKpi(branchId)).thenReturn(kpiProj);

                        // Capital limit = 2,500,000,000 (from branch1) -> 500M / 2500M * 100 = 20.0%
                        // Collected interest = 25M, upcoming = 75M -> expected = 100M -> 25M / 100M *
                        // 100 = 25.0%
                        when(contractService.getCollectedInterestInPeriod(eq(branchId), any(), any()))
                                        .thenReturn(new BigDecimal("25000000"));
                        when(contractService.getUpcomingCycleInterest(eq(branchId), any()))
                                        .thenReturn(new BigDecimal("75000000"));

                        Page<ContractResponse> emptyPage = new PageImpl<>(Collections.emptyList(),
                                        PageRequest.of(0, 10), 0);
                        when(contractService.findDashboardContractPage(any(), any(), any(), any(), any()))
                                        .thenReturn(emptyPage);

                        DashboardSummaryResponse summary = dashboardService.getDashboardSummary(branchId);
                        DashboardSummaryResponse.KpiSummary kpi = summary.getKpi();

                        assertThat(kpi.getDisbursedPrincipalAmount()).isEqualByComparingTo(new BigDecimal("500000000"));
                        assertThat(kpi.getCapitalLimitAmount()).isEqualByComparingTo(new BigDecimal("2500000000"));
                        assertThat(kpi.getCapitalUtilizationPercent()).isEqualByComparingTo(new BigDecimal("20.0"));

                        assertThat(kpi.getCollectedInterestAmount()).isEqualByComparingTo(new BigDecimal("25000000"));
                        assertThat(kpi.getExpectedInterestAmount()).isEqualByComparingTo(new BigDecimal("100000000"));
                        assertThat(kpi.getInterestCollectionPercent()).isEqualByComparingTo(new BigDecimal("25.0"));

                        assertThat(kpi.getOverdueOnlyCount()).isEqualTo(2L); // 3 overdue - 1 waiting liquidation = 2
                }

                @Test
                @DisplayName("Should safely handle zero divisors without throwing ArithmeticException")
                void shouldHandleZeroDivisorsSafely() {
                        Long branchId = 1L;
                        stubStandardSummaryDependencies(branchId);

                        // Capital limit set to ZERO
                        BranchResponse zeroCapBranch = BranchResponse.builder()
                                        .id(1L)
                                        .capitalLimit(BigDecimal.ZERO)
                                        .build();
                        when(branchService.getActiveBranches()).thenReturn(List.of(zeroCapBranch));

                        DashboardKpiProjection kpiProj = mock(DashboardKpiProjection.class);
                        when(kpiProj.getDisbursedPrincipal()).thenReturn(new BigDecimal("100000000"));
                        when(contractService.getPortfolioKpi(branchId)).thenReturn(kpiProj);

                        // Zero collected and zero upcoming interest -> expected interest = ZERO
                        when(contractService.getCollectedInterestInPeriod(eq(branchId), any(), any()))
                                        .thenReturn(BigDecimal.ZERO);
                        when(contractService.getUpcomingCycleInterest(eq(branchId), any()))
                                        .thenReturn(BigDecimal.ZERO);

                        Page<ContractResponse> emptyPage = new PageImpl<>(Collections.emptyList(),
                                        PageRequest.of(0, 10), 0);
                        when(contractService.findDashboardContractPage(any(), any(), any(), any(), any()))
                                        .thenReturn(emptyPage);

                        DashboardSummaryResponse summary = dashboardService.getDashboardSummary(branchId);
                        DashboardSummaryResponse.KpiSummary kpi = summary.getKpi();

                        assertThat(kpi.getCapitalUtilizationPercent()).isEqualByComparingTo(BigDecimal.ZERO);
                        assertThat(kpi.getInterestCollectionPercent()).isEqualByComparingTo(BigDecimal.ZERO);
                }
        }

        @Nested
        @DisplayName("7. RBAC Branch Isolation & Fallbacks")
        class RbacAndFallbackTests {

                @Test
                @DisplayName("Should propagate BusinessException when BranchSecurityGuard denies branch access (4004)")
                void shouldPropagateAccessDeniedFromSecurityGuard() {
                        Long unauthorizedBranchId = 99L;
                        when(branchSecurityGuard.resolveAuthorizedBranchId(unauthorizedBranchId))
                                        .thenThrow(new BusinessException(ErrorCode.BRANCH_ACCESS_DENIED,
                                                        "Access denied to branch 99"));

                        assertThatThrownBy(() -> dashboardService.getDashboardSummary(unauthorizedBranchId))
                                        .isInstanceOf(BusinessException.class)
                                        .hasMessageContaining("Access denied to branch 99");

                        assertThatThrownBy(() -> dashboardService.getDashboardContractPage(
                                        unauthorizedBranchId, DashboardTimeframe.THIS_MONTH, DashboardContractTab.ALL,
                                        null, 0, 10))
                                        .isInstanceOf(BusinessException.class)
                                        .hasMessageContaining("Access denied to branch 99");
                }

                @Test
                @DisplayName("Should support ADMIN system-wide access when branchId is null")
                void shouldSupportAdminSystemWideWhenBranchIdIsNull() {
                        UserPrincipal adminUser = UserPrincipal.builder()
                                        .id(999L)
                                        .username("admin")
                                        .role(RoleEnum.ADMIN)
                                        .build();
                        when(branchSecurityGuard.getCurrentUserOrNull()).thenReturn(adminUser);

                        stubStandardSummaryDependencies(null);

                        Page<ContractResponse> emptyPage = new PageImpl<>(Collections.emptyList(),
                                        PageRequest.of(0, 10), 0);
                        when(contractService.findDashboardContractPage(
                                        isNull(), any(), eq(DashboardContractTab.ALL), isNull(), any()))
                                        .thenReturn(emptyPage);

                        DashboardSummaryResponse summary = dashboardService.getDashboardSummary(null);

                        assertThat(summary.getSelectedBranchId()).isNull();
                        assertThat(summary.getSelectedBranchCode()).isEqualTo("ALL");
                        assertThat(summary.getSelectedBranchName()).isEqualTo("Toàn hệ thống (Tất cả chi nhánh)");
                        assertThat(summary.isCanSwitchBranch()).isTrue();
                        assertThat(summary.getBranches()).containsExactly(branch1, branch2);
                }

                @Test
                @DisplayName("Should return strictly 1 assigned branch in summary.branches for single-branch users (MANAGER/STAFF/ACCOUNTANT)")
                void shouldReturnOnlyAssignedBranchForSingleBranchUsers() {
                        UserPrincipal managerBranch2 = UserPrincipal.builder()
                                        .id(202L)
                                        .username("manager_b2")
                                        .role(RoleEnum.MANAGER)
                                        .branchId(2L)
                                        .branchCode("CN02")
                                        .branchName("Chi nhánh Bình Thạnh")
                                        .build();
                        when(branchSecurityGuard.getCurrentUserOrNull()).thenReturn(managerBranch2);

                        stubStandardSummaryDependencies(2L);
                        when(branchSecurityGuard.resolveAuthorizedBranchId(null)).thenReturn(2L);

                        Page<ContractResponse> emptyPage = new PageImpl<>(Collections.emptyList(),
                                        PageRequest.of(0, 10), 0);
                        when(contractService.findDashboardContractPage(
                                        eq(2L), any(), eq(DashboardContractTab.ALL), isNull(), any()))
                                        .thenReturn(emptyPage);

                        DashboardSummaryResponse summary = dashboardService.getDashboardSummary(null);

                        assertThat(summary.isCanSwitchBranch()).isFalse();
                        assertThat(summary.getUserRole()).isEqualTo("MANAGER");
                        assertThat(summary.getSelectedBranchId()).isEqualTo(2L);
                        assertThat(summary.getBranches()).hasSize(1);
                        assertThat(summary.getBranches().get(0).getId()).isEqualTo(2L);
                        assertThat(summary.getBranches().get(0).getBranchCode()).isEqualTo("CN02");
                }

                @Test
                @DisplayName("Should fallback to first active branch when unauthenticated and branchId is null")
                void shouldFallbackToFirstBranchWhenUnauthenticatedAndNoBranchSpecified() {
                        when(branchSecurityGuard.getCurrentUserOrNull()).thenReturn(null);

                        stubStandardSummaryDependencies(1L);

                        Page<ContractResponse> emptyPage = new PageImpl<>(Collections.emptyList(),
                                        PageRequest.of(0, 10), 0);
                        when(contractService.findDashboardContractPage(
                                        eq(1L), any(), eq(DashboardContractTab.ALL), isNull(), any()))
                                        .thenReturn(emptyPage);

                        DashboardSummaryResponse summary = dashboardService.getDashboardSummary(null);

                        assertThat(summary.getSelectedBranchId()).isEqualTo(1L);
                        assertThat(summary.getSelectedBranchCode()).isEqualTo("CN01");
                        assertThat(summary.getSelectedBranchName()).isEqualTo("Chi nhánh Quận 1");
                }

                @Test
                @DisplayName("Should fail closed without system-wide queries when non-admin user has branchId == null")
                void shouldFailClosedWithoutSystemWideQueriesWhenNonAdminHasNoAssignedBranch() {
                        UserPrincipal unassignedStaff = UserPrincipal.builder()
                                        .id(303L)
                                        .username("staff_unassigned")
                                        .role(RoleEnum.STAFF)
                                        .branchId(null)
                                        .build();
                        when(branchSecurityGuard.getCurrentUserOrNull()).thenReturn(unassignedStaff);
                        when(branchSecurityGuard.resolveAuthorizedBranchId(null)).thenReturn(null);

                        DashboardSummaryResponse summary = dashboardService.getDashboardSummary(null);
                        DashboardContractPageResponse page = dashboardService.getDashboardContractPage(
                                        null, DashboardTimeframe.THIS_MONTH, DashboardContractTab.ALL, null, 0, 10);

                        assertThat(summary.getSelectedBranchId()).isNull();
                        assertThat(summary.getBranches()).isEmpty();
                        assertThat(summary.isCanSwitchBranch()).isFalse();
                        assertThat(summary.getUserRole()).isEqualTo("STAFF");
                        assertThat(summary.getContracts()).isEmpty();
                        assertThat(page.getItems()).isEmpty();
                        assertThat(page.getTotalElements()).isZero();

                        verify(branchService, never()).getActiveBranches();
                        verify(contractService, never()).getPortfolioKpi(any());
                        verify(contractService, never()).findDashboardContractPage(any(), any(), any(), any(), any());
                }

                @Test
                @DisplayName("Should synthesize principal fallback branch in summary.branches when BranchService lookup fails")
                void shouldSynthesizePrincipalFallbackBranchWhenBranchServiceLookupFails() {
                        UserPrincipal managerBranch2 = UserPrincipal.builder()
                                        .id(204L)
                                        .username("manager_b2_fallback")
                                        .role(RoleEnum.MANAGER)
                                        .branchId(2L)
                                        .branchCode("CN02")
                                        .branchName("Chi nhánh Bình Thạnh")
                                        .build();
                        when(branchSecurityGuard.getCurrentUserOrNull()).thenReturn(managerBranch2);
                        when(branchSecurityGuard.resolveAuthorizedBranchId(null)).thenReturn(2L);
                        when(branchService.getActiveBranches()).thenReturn(List.of());
                        when(branchService.getAllBranches()).thenReturn(List.of());
                        when(branchService.getBranchById(2L)).thenThrow(new RuntimeException("Branch lookup unavailable"));

                        Page<ContractResponse> emptyPage = new PageImpl<>(Collections.emptyList(),
                                        PageRequest.of(0, 10), 0);
                        when(contractService.findDashboardContractPage(
                                        eq(2L), any(), eq(DashboardContractTab.ALL), isNull(), any()))
                                        .thenReturn(emptyPage);

                        DashboardSummaryResponse summary = dashboardService.getDashboardSummary(null);

                        assertThat(summary.getSelectedBranchId()).isEqualTo(2L);
                        assertThat(summary.isCanSwitchBranch()).isFalse();
                        assertThat(summary.getBranches()).hasSize(1);
                        assertThat(summary.getBranches().get(0).getId()).isEqualTo(2L);
                        assertThat(summary.getBranches().get(0).getBranchCode()).isEqualTo("CN02");
                        assertThat(summary.getBranches().get(0).getBranchName()).isEqualTo("Chi nhánh Bình Thạnh");
                }
        }
}

package com.pawnmaster.api.modules.dashboard.dto;

import com.pawnmaster.api.modules.branch.dto.BranchResponse;
import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import com.pawnmaster.api.modules.dashboard.DashboardTimeframe;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Aggregated real-time payload returned by {@code GET /api/v1/dashboard/summary}
 * to hydrate the Dashboard Topbar, 4 KPI Cards, 2 Charts, 6 Filter Tab Counters, and 7-Column Contract Table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryResponse {

    // 1. Branch & User Scope Metadata
    private Long selectedBranchId;
    private String selectedBranchCode;
    private String selectedBranchName;
    private String targetMonth;
    private Integer targetMonthNumber;
    private BigDecimal currentCashBalance;
    private BigDecimal capitalLimit;

    // 1b. Timeframe Context Metadata
    private DashboardTimeframe selectedTimeframe;
    private String timeframeLabel;
    private String timeframeRangeLabel;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;

    private Long userAssignedBranchId;
    private String userAssignedBranchCode;
    private String userAssignedBranchName;
    private String userRole;
    private boolean canSwitchBranch;

    private List<BranchResponse> branches;

    // 2. KPI Metrics
    private KpiSummary kpi;

    // 3. Filter Tab Counts (ALL, PENDING_APPROVAL, ACTIVE, DUE_TODAY, OVERDUE, WAITING_LIQUIDATION)
    private Map<String, Long> tabCounts;

    // 4. Cash Flow Chart Datasets (days, months, year)
    private Map<String, CashFlowChartSeries> cashFlowCharts;

    // 5. Collateral Donut Chart Distribution
    private List<CollateralCategoryShare> collateralDistribution;

    // 6. Contracts List (Retained for 100% backward compatibility)
    private List<ContractResponse> contracts;

    // 6b. Paginated Contract Slice (strictly Page 0 on initial summary load)
    private DashboardContractPageResponse contractPage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KpiSummary {
        private long activeContractsCount;
        private long pendingApprovalCount;
        private BigDecimal disbursedPrincipalAmount;
        private BigDecimal capitalLimitAmount;
        private BigDecimal capitalUtilizationPercent;

        private BigDecimal expectedInterestAmount;
        private BigDecimal collectedInterestAmount;
        private BigDecimal interestCollectionPercent;

        private long overdueContractsCount;
        private long overdueOnlyCount;
        private long waitingLiquidationCount;
        private BigDecimal atRiskPrincipalAmount;

        private BigDecimal currentCashBalance;
        private BigDecimal totalCashIn;
        private BigDecimal totalCashOut;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CashFlowChartSeries {
        private List<String> labels;
        private List<BigDecimal> disburseMillions;
        private List<BigDecimal> collectMillions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollateralCategoryShare {
        private String groupKey;
        private String label;
        private long contractCount;
        private BigDecimal percentage;
        private BigDecimal totalAppraisedValue;
    }
}


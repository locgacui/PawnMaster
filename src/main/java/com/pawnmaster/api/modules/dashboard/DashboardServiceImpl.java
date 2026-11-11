package com.pawnmaster.api.modules.dashboard;

import com.pawnmaster.api.modules.branch.BranchService;
import com.pawnmaster.api.modules.branch.dto.BranchResponse;
import com.pawnmaster.api.modules.cashbook.CashbookService;
import com.pawnmaster.api.modules.cashbook.TransactionType;
import com.pawnmaster.api.modules.cashbook.dto.CashFlowSummaryResponse;
import com.pawnmaster.api.modules.cashbook.dto.CashbookResponse;
import com.pawnmaster.api.modules.contract.ContractService;
import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import com.pawnmaster.api.modules.contract.dto.DashboardKpiProjection;
import com.pawnmaster.api.modules.dashboard.dto.DashboardContractPageResponse;
import com.pawnmaster.api.modules.dashboard.dto.DashboardSummaryResponse;
import com.pawnmaster.api.security.BranchSecurityGuard;
import com.pawnmaster.api.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service implementation for Dashboard summary aggregation (strictly Page 0)
 * and server-side on-demand contract pagination.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private static final DateTimeFormatter MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MM/yyyy");

    private final BranchService branchService;
    private final ContractService contractService;
    private final CashbookService cashbookService;
    private final BranchSecurityGuard branchSecurityGuard;

    @Autowired
    public DashboardServiceImpl(
            BranchService branchService,
            ContractService contractService,
            CashbookService cashbookService,
            @Autowired(required = false) BranchSecurityGuard branchSecurityGuard
    ) {
        this.branchService = branchService;
        this.contractService = contractService;
        this.cashbookService = cashbookService;
        this.branchSecurityGuard = branchSecurityGuard != null ? branchSecurityGuard : new BranchSecurityGuard();
    }

    public DashboardServiceImpl(
            BranchService branchService,
            ContractService contractService,
            CashbookService cashbookService
    ) {
        this(branchService, contractService, cashbookService, new BranchSecurityGuard());
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getDashboardSummary(
            Long branchId,
            DashboardTimeframe timeframe,
            int size,
            Integer legacyMonth
    ) {
        LocalDate referenceDate;
        if (legacyMonth != null && legacyMonth >= 1 && legacyMonth <= 12) {
            referenceDate = LocalDate.of(LocalDate.now().getYear(), legacyMonth, 1);
        } else {
            referenceDate = LocalDate.now();
        }
        return getDashboardSummaryInternal(branchId, timeframe, size, legacyMonth, referenceDate);
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getDashboardSummary(
            Long branchId,
            DashboardTimeframe timeframe,
            int size,
            String legacyMonth
    ) {
        YearMonth ym = parseYearMonthSafe(legacyMonth);
        Integer monthNum = (legacyMonth != null && !legacyMonth.isBlank()) ? ym.getMonthValue() : null;
        LocalDate referenceDate = ym.atDay(1);
        return getDashboardSummaryInternal(branchId, timeframe, size, monthNum, referenceDate);
    }

    private DashboardSummaryResponse getDashboardSummaryInternal(
            Long branchId,
            DashboardTimeframe timeframe,
            int rawSize,
            Integer legacyMonthNumber,
            LocalDate referenceDate
    ) {
        // 1. Branch RBAC Enforcement
        Long authorizedBranchId = branchSecurityGuard != null
                ? branchSecurityGuard.resolveAuthorizedBranchId(branchId)
                : branchId;

        UserPrincipal currentUser = branchSecurityGuard != null
                ? branchSecurityGuard.getCurrentUserOrNull()
                : null;

        // 2. Timeframe & Period Resolution
        DashboardTimeframe tf = timeframe != null ? timeframe : DashboardTimeframe.THIS_MONTH;
        DashboardPeriod period = tf.calculatePeriod(referenceDate);
        String targetMonthStr = (legacyMonthNumber != null)
                ? String.format("%02d/%d", legacyMonthNumber, referenceDate.getYear())
                : referenceDate.format(MONTH_YEAR_FORMATTER);
        int targetMonthNum = (legacyMonthNumber != null) ? legacyMonthNumber : referenceDate.getMonthValue();
        int clampedSize = clampSize(rawSize);

        // Fail-closed guard: an authenticated non-admin principal without an assigned branch
        // must never trigger branch or null-branch (system-wide) repository queries.
        boolean canSwitchBranch = currentUser == null
                || (currentUser.getRole() != null && currentUser.getRole().canSwitchBranch());
        if (!canSwitchBranch && authorizedBranchId == null
                && (currentUser == null || currentUser.getBranchId() == null)) {
            return buildEmptyIsolatedSummary(tf, period, targetMonthStr, targetMonthNum, clampedSize, currentUser);
        }

        List<BranchResponse> branches = loadActiveBranchesSafe();
        DashboardBranchScope scope = DashboardBranchScope.resolve(authorizedBranchId, branches, currentUser, branchService);
        BranchResponse selectedBranch = scope.selectedBranch();
        Long effectiveBranchId = selectedBranch != null ? selectedBranch.getId() : authorizedBranchId;

        if (!scope.canSwitchBranch() && effectiveBranchId == null) {
            return buildEmptyIsolatedSummary(tf, period, targetMonthStr, targetMonthNum, clampedSize, currentUser);
        }

        // 3. Treasury Balance & Capital Limit
        CashFlowSummaryResponse cashFlowSummary = loadCashFlowSummarySafe(effectiveBranchId, selectedBranch, branches);
        BigDecimal cashBalance = cashFlowSummary != null && cashFlowSummary.getCurrentCashBalance() != null
                ? cashFlowSummary.getCurrentCashBalance()
                : BigDecimal.ZERO;

        BigDecimal capitalLimit = resolveCapitalLimit(selectedBranch, branches);

        // 4. Lightweight SQL KPI Aggregations
        DashboardSummaryResponse.KpiSummary kpiSummary = buildKpiSummary(
                effectiveBranchId,
                period,
                cashBalance,
                capitalLimit,
                cashFlowSummary
        );

        // 5. Tab Counts Projection
        Map<String, Long> tabCounts = loadTabCountsSafe(effectiveBranchId, period);

        // 6. Cash Flow Charts for All 5 Timeframes + Legacy Aliases
        Map<String, DashboardSummaryResponse.CashFlowChartSeries> cashFlowCharts =
                buildCashFlowCharts(effectiveBranchId, referenceDate);

        // 7. Collateral Distribution Grouping
        List<DashboardSummaryResponse.CollateralCategoryShare> collateralDist =
                loadCollateralDistributionSafe(effectiveBranchId);

        // 8. Strictly Page 0 Contract Retrieval (No Page 2+ Preloaded!)
        DashboardContractPageResponse contractPage = loadPage0ContractPageSafe(effectiveBranchId, period, clampedSize);

        // 9. Assemble Response DTO
        Long assignedBranchId = currentUser != null
                ? currentUser.getBranchId()
                : (selectedBranch != null ? selectedBranch.getId() : null);
        boolean matchesSelectedBranch = selectedBranch != null
                && Objects.equals(selectedBranch.getId(), assignedBranchId);
        String assignedBranchCode = currentUser != null
                ? (currentUser.getBranchCode() != null
                        ? currentUser.getBranchCode()
                        : (matchesSelectedBranch ? selectedBranch.getBranchCode() : null))
                : (selectedBranch != null ? selectedBranch.getBranchCode() : null);
        String assignedBranchName = currentUser != null
                ? (currentUser.getBranchName() != null
                        ? currentUser.getBranchName()
                        : (matchesSelectedBranch ? selectedBranch.getBranchName() : null))
                : (selectedBranch != null ? selectedBranch.getBranchName() : null);

        return DashboardSummaryResponse.builder()
                .selectedBranchId(selectedBranch != null ? selectedBranch.getId() : null)
                .selectedBranchCode(selectedBranch != null ? selectedBranch.getBranchCode() : "ALL")
                .selectedBranchName(selectedBranch != null ? selectedBranch.getBranchName() : "Toàn hệ thống (Tất cả chi nhánh)")
                .targetMonth(targetMonthStr)
                .targetMonthNumber(targetMonthNum)
                .currentCashBalance(cashBalance)
                .capitalLimit(capitalLimit)
                .selectedTimeframe(tf)
                .timeframeLabel(period.label())
                .timeframeRangeLabel(period.rangeLabel())
                .periodStartDate(period.startDate())
                .periodEndDate(period.endDate())
                .userAssignedBranchId(assignedBranchId)
                .userAssignedBranchCode(assignedBranchCode)
                .userAssignedBranchName(assignedBranchName)
                .userRole(currentUser != null && currentUser.getRole() != null ? currentUser.getRole().name() : null)
                .canSwitchBranch(scope.canSwitchBranch())
                .branches(scope.visibleBranches())
                .kpi(kpiSummary)
                .tabCounts(tabCounts)
                .cashFlowCharts(cashFlowCharts)
                .collateralDistribution(collateralDist)
                .contractPage(contractPage)
                .contracts(contractPage != null ? contractPage.getItems() : List.of())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardContractPageResponse getDashboardContractPage(
            Long branchId,
            DashboardTimeframe timeframe,
            DashboardContractTab tab,
            String keyword,
            int page,
            int size
    ) {
        Long authorizedBranchId = branchSecurityGuard != null
                ? branchSecurityGuard.resolveAuthorizedBranchId(branchId)
                : branchId;
        UserPrincipal currentUser = branchSecurityGuard != null
                ? branchSecurityGuard.getCurrentUserOrNull()
                : null;

        int clampedPage = Math.max(0, page);
        int clampedSize = clampSize(size);
        DashboardTimeframe tf = timeframe != null ? timeframe : DashboardTimeframe.THIS_MONTH;
        DashboardPeriod period = tf.calculatePeriod();
        DashboardContractTab effectiveTab = tab != null ? tab : DashboardContractTab.ALL;
        String normalizedKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        boolean canSwitchBranch = currentUser == null
                || (currentUser.getRole() != null && currentUser.getRole().canSwitchBranch());
        if (!canSwitchBranch && authorizedBranchId == null) {
            return DashboardContractPageResponse.empty(clampedSize, effectiveTab.name(), normalizedKeyword);
        }

        Page<ContractResponse> pageResult = null;
        try {
            pageResult = contractService.findDashboardContractPage(
                    authorizedBranchId,
                    period,
                    effectiveTab,
                    normalizedKeyword,
                    PageRequest.of(clampedPage, clampedSize)
            );
        } catch (Exception e) {
            log.warn("Error fetching dashboard contract page for branch {}: {}", authorizedBranchId, e.getMessage());
        }

        if (pageResult != null) {
            return DashboardContractPageResponse.of(
                    pageResult.getContent(),
                    clampedPage,
                    clampedSize,
                    pageResult.getTotalElements(),
                    effectiveTab.name(),
                    normalizedKeyword
            );
        }
        return DashboardContractPageResponse.empty(clampedSize, effectiveTab.name(), normalizedKeyword);
    }

    private DashboardSummaryResponse.KpiSummary buildKpiSummary(
            Long effectiveBranchId,
            DashboardPeriod period,
            BigDecimal cashBalance,
            BigDecimal capitalLimit,
            CashFlowSummaryResponse cashFlowSummary
    ) {
        DashboardKpiProjection kpiProj = null;
        try {
            kpiProj = contractService.getPortfolioKpi(effectiveBranchId);
        } catch (Exception e) {
            log.warn("Error fetching portfolio KPI for branch {}: {}", effectiveBranchId, e.getMessage());
        }

        if (kpiProj == null) {
            try {
                kpiProj = contractService.getPeriodKpi(effectiveBranchId, period);
            } catch (Exception ignored) {
            }
        }

        long activeCount = kpiProj != null ? kpiProj.getActiveCount() : 0L;
        long pendingCount = kpiProj != null ? kpiProj.getPendingCount() : 0L;
        BigDecimal disbursedPrincipal = (kpiProj != null && kpiProj.getDisbursedPrincipal() != null)
                ? kpiProj.getDisbursedPrincipal()
                : BigDecimal.ZERO;
        long overdueCount = kpiProj != null ? kpiProj.getOverdueCount() : 0L;
        long waitingLiquidationCount = kpiProj != null ? kpiProj.getWaitingLiquidationCount() : 0L;
        BigDecimal atRiskPrincipal = (kpiProj != null && kpiProj.getAtRiskPrincipal() != null)
                ? kpiProj.getAtRiskPrincipal()
                : BigDecimal.ZERO;
        long overdueOnlyCount = Math.max(0L, overdueCount - waitingLiquidationCount);

        // Capital utilization percent
        BigDecimal capUtilPct = BigDecimal.ZERO;
        if (capitalLimit != null && capitalLimit.compareTo(BigDecimal.ZERO) > 0) {
            capUtilPct = disbursedPrincipal.multiply(new BigDecimal("100"))
                    .divide(capitalLimit, 1, RoundingMode.HALF_UP);
        }

        // Collected interest in scoped period
        BigDecimal collectedInterest = BigDecimal.ZERO;
        try {
            collectedInterest = contractService.getCollectedInterestInPeriod(
                    effectiveBranchId,
                    period.fromDateTime(),
                    period.toDateTimeExclusive()
            );
        } catch (Exception e) {
            log.warn("Error fetching collected interest in period: {}", e.getMessage());
        }
        if (collectedInterest == null) {
            collectedInterest = BigDecimal.ZERO;
        }

        // Upcoming cycle interest
        BigDecimal upcomingCycleInterest = BigDecimal.ZERO;
        try {
            upcomingCycleInterest = contractService.getUpcomingCycleInterest(effectiveBranchId, period);
        } catch (Exception e) {
            log.warn("Error fetching upcoming cycle interest: {}", e.getMessage());
        }
        if (upcomingCycleInterest == null) {
            upcomingCycleInterest = BigDecimal.ZERO;
        }

        BigDecimal expectedInterest = collectedInterest.add(upcomingCycleInterest);
        BigDecimal interestProgressPct = BigDecimal.ZERO;
        if (expectedInterest.compareTo(BigDecimal.ZERO) > 0) {
            interestProgressPct = collectedInterest.multiply(new BigDecimal("100"))
                    .divide(expectedInterest, 1, RoundingMode.HALF_UP);
        }

        return DashboardSummaryResponse.KpiSummary.builder()
                .activeContractsCount(activeCount)
                .pendingApprovalCount(pendingCount)
                .disbursedPrincipalAmount(disbursedPrincipal)
                .capitalLimitAmount(capitalLimit)
                .capitalUtilizationPercent(capUtilPct)
                .expectedInterestAmount(expectedInterest)
                .collectedInterestAmount(collectedInterest)
                .interestCollectionPercent(interestProgressPct)
                .overdueContractsCount(overdueCount)
                .overdueOnlyCount(overdueOnlyCount)
                .waitingLiquidationCount(waitingLiquidationCount)
                .atRiskPrincipalAmount(atRiskPrincipal)
                .currentCashBalance(cashBalance)
                .totalCashIn(cashFlowSummary != null && cashFlowSummary.getTotalCashIn() != null
                        ? cashFlowSummary.getTotalCashIn() : BigDecimal.ZERO)
                .totalCashOut(cashFlowSummary != null && cashFlowSummary.getTotalCashOut() != null
                        ? cashFlowSummary.getTotalCashOut() : BigDecimal.ZERO)
                .build();
    }

    private Map<String, DashboardSummaryResponse.CashFlowChartSeries> buildCashFlowCharts(
            Long effectiveBranchId,
            LocalDate referenceDate
    ) {
        // Broadest range across all 5 timeframes:
        // LAST_6_MONTHS goes back 5 months; THIS_YEAR starts at Jan 1.
        LocalDate startOfThisYear = referenceDate.withDayOfYear(1);
        LocalDate startOfLast6Months = YearMonth.from(referenceDate).minusMonths(5).atDay(1);
        LocalDate minFromDate = startOfThisYear.isBefore(startOfLast6Months) ? startOfThisYear : startOfLast6Months;
        LocalDate maxToDate = referenceDate.withDayOfYear(referenceDate.lengthOfYear());

        LocalDateTime minFromTime = minFromDate.atStartOfDay();
        LocalDateTime maxToTime = maxToDate.plusDays(1).atStartOfDay();

        List<CashbookResponse> transactions = Collections.emptyList();
        try {
            transactions = cashbookService.getTransactionsByScopeAndPeriod(effectiveBranchId, minFromTime, maxToTime);
        } catch (Exception e) {
            log.warn("getTransactionsByScopeAndPeriod failed: {}", e.getMessage());
        }
        if (transactions == null || transactions.isEmpty()) {
            try {
                if (effectiveBranchId != null) {
                    transactions = cashbookService.getTransactionsByBranch(effectiveBranchId);
                } else {
                    transactions = cashbookService.getAllTransactions();
                }
            } catch (Exception ignored) {
            }
        }
        if (transactions == null) {
            transactions = Collections.emptyList();
        }

        Map<String, DashboardSummaryResponse.CashFlowChartSeries> result = new LinkedHashMap<>();
        BigDecimal oneMillion = new BigDecimal("1000000");

        for (DashboardTimeframe tf : DashboardTimeframe.values()) {
            DashboardPeriod period = tf.calculatePeriod(referenceDate);
            List<ChartBucket> buckets = period.buckets();
            List<String> labels = new ArrayList<>(buckets.size());
            List<BigDecimal> disburseMillions = new ArrayList<>(buckets.size());
            List<BigDecimal> collectMillions = new ArrayList<>(buckets.size());

            for (ChartBucket bucket : buckets) {
                labels.add(bucket.label());
                BigDecimal disburseSum = BigDecimal.ZERO;
                BigDecimal collectSum = BigDecimal.ZERO;

                for (CashbookResponse tx : transactions) {
                    if (tx.getTransactionDate() != null && bucket.contains(tx.getTransactionDate())) {
                        BigDecimal amount = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
                        if (tx.getTransactionType() == TransactionType.CASH_OUT) {
                            disburseSum = disburseSum.add(amount);
                        } else if (tx.getTransactionType() == TransactionType.CASH_IN) {
                            collectSum = collectSum.add(amount);
                        }
                    }
                }

                disburseMillions.add(disburseSum.divide(oneMillion, 2, RoundingMode.HALF_UP));
                collectMillions.add(collectSum.divide(oneMillion, 2, RoundingMode.HALF_UP));
            }

            DashboardSummaryResponse.CashFlowChartSeries series = DashboardSummaryResponse.CashFlowChartSeries.builder()
                    .labels(labels)
                    .disburseMillions(disburseMillions)
                    .collectMillions(collectMillions)
                    .build();

            result.put(tf.name(), series);
            if (tf.getLegacyChartKey() != null) {
                result.put(tf.getLegacyChartKey(), series);
            }
        }

        return result;
    }

    private DashboardContractPageResponse loadPage0ContractPageSafe(
            Long effectiveBranchId,
            DashboardPeriod period,
            int clampedSize
    ) {
        Page<ContractResponse> pageResult = null;
        try {
            pageResult = contractService.findDashboardContractPage(
                    effectiveBranchId,
                    period,
                    DashboardContractTab.ALL,
                    null,
                    PageRequest.of(0, clampedSize)
            );
        } catch (Exception e) {
            log.warn("Error loading Page 0 dashboard contracts for branch {}: {}", effectiveBranchId, e.getMessage());
        }

        if (pageResult != null) {
            return DashboardContractPageResponse.of(
                    pageResult.getContent(),
                    0,
                    clampedSize,
                    pageResult.getTotalElements(),
                    DashboardContractTab.ALL.name(),
                    null
            );
        }
        return DashboardContractPageResponse.empty(clampedSize, DashboardContractTab.ALL.name(), null);
    }

    private Map<String, Long> loadTabCountsSafe(Long effectiveBranchId, DashboardPeriod period) {
        Map<String, Long> tabCounts = null;
        try {
            tabCounts = contractService.getDashboardTabCounts(effectiveBranchId, period);
        } catch (Exception e) {
            log.warn("Error fetching tab counts for branch {}: {}", effectiveBranchId, e.getMessage());
        }

        if (tabCounts == null || tabCounts.isEmpty()) {
            tabCounts = new LinkedHashMap<>();
            tabCounts.put("ALL", 0L);
            tabCounts.put("PENDING_APPROVAL", 0L);
            tabCounts.put("ACTIVE", 0L);
            tabCounts.put("DUE_TODAY", 0L);
            tabCounts.put("OVERDUE", 0L);
            tabCounts.put("WAITING_LIQUIDATION", 0L);
        }
        return tabCounts;
    }

    private List<DashboardSummaryResponse.CollateralCategoryShare> loadCollateralDistributionSafe(Long effectiveBranchId) {
        try {
            List<DashboardSummaryResponse.CollateralCategoryShare> dist =
                    contractService.getDashboardCollateralDistribution(effectiveBranchId);
            if (dist != null) {
                return dist;
            }
        } catch (Exception e) {
            log.warn("Error fetching collateral distribution for branch {}: {}", effectiveBranchId, e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<BranchResponse> loadActiveBranchesSafe() {
        List<BranchResponse> branches = Collections.emptyList();
        try {
            branches = branchService.getActiveBranches();
            if (branches == null || branches.isEmpty()) {
                branches = branchService.getAllBranches();
            }
        } catch (Exception e) {
            log.warn("Unable to fetch branches: {}", e.getMessage());
        }
        return branches != null ? branches : Collections.emptyList();
    }

    private DashboardSummaryResponse buildEmptyIsolatedSummary(
            DashboardTimeframe tf,
            DashboardPeriod period,
            String targetMonthStr,
            int targetMonthNum,
            int clampedSize,
            UserPrincipal currentUser
    ) {
        DashboardContractPageResponse emptyPage = DashboardContractPageResponse.empty(clampedSize, DashboardContractTab.ALL.name(), null);
        DashboardSummaryResponse.KpiSummary zeroKpi = DashboardSummaryResponse.KpiSummary.builder()
                .disbursedPrincipalAmount(BigDecimal.ZERO)
                .capitalLimitAmount(BigDecimal.ZERO)
                .capitalUtilizationPercent(BigDecimal.ZERO)
                .expectedInterestAmount(BigDecimal.ZERO)
                .collectedInterestAmount(BigDecimal.ZERO)
                .interestCollectionPercent(BigDecimal.ZERO)
                .atRiskPrincipalAmount(BigDecimal.ZERO)
                .currentCashBalance(BigDecimal.ZERO)
                .totalCashIn(BigDecimal.ZERO)
                .totalCashOut(BigDecimal.ZERO)
                .build();

        return DashboardSummaryResponse.builder()
                .selectedBranchId(null)
                .selectedBranchCode(null)
                .selectedBranchName(null)
                .targetMonth(targetMonthStr)
                .targetMonthNumber(targetMonthNum)
                .currentCashBalance(BigDecimal.ZERO)
                .capitalLimit(BigDecimal.ZERO)
                .selectedTimeframe(tf)
                .timeframeLabel(period.label())
                .timeframeRangeLabel(period.rangeLabel())
                .periodStartDate(period.startDate())
                .periodEndDate(period.endDate())
                .userRole(currentUser != null && currentUser.getRole() != null ? currentUser.getRole().name() : null)
                .canSwitchBranch(false)
                .branches(List.of())
                .kpi(zeroKpi)
                .tabCounts(Map.of(
                        "ALL", 0L,
                        "PENDING_APPROVAL", 0L,
                        "ACTIVE", 0L,
                        "DUE_TODAY", 0L,
                        "OVERDUE", 0L,
                        "WAITING_LIQUIDATION", 0L
                ))
                .cashFlowCharts(Map.of())
                .collateralDistribution(List.of())
                .contractPage(emptyPage)
                .contracts(List.of())
                .build();
    }

    private CashFlowSummaryResponse loadCashFlowSummarySafe(
            Long effectiveBranchId,
            BranchResponse selectedBranch,
            List<BranchResponse> branches
    ) {
        CashFlowSummaryResponse cashFlowSummary = null;
        try {
            cashFlowSummary = cashbookService.getBranchCashFlowSummary(effectiveBranchId);
        } catch (Exception e) {
            log.warn("Unable to fetch cash flow summary: {}", e.getMessage());
        }

        if (cashFlowSummary == null) {
            BigDecimal balance = BigDecimal.ZERO;
            if (selectedBranch != null && selectedBranch.getCurrentCashBalance() != null) {
                balance = selectedBranch.getCurrentCashBalance();
            } else if (branches != null && !branches.isEmpty()) {
                balance = branches.stream()
                        .map(b -> b.getCurrentCashBalance() != null ? b.getCurrentCashBalance() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            cashFlowSummary = CashFlowSummaryResponse.builder()
                    .branchId(effectiveBranchId)
                    .branchName(selectedBranch != null ? selectedBranch.getBranchName() : "Toàn hệ thống")
                    .currentCashBalance(balance)
                    .build();
        }
        return cashFlowSummary;
    }

    private BigDecimal resolveCapitalLimit(BranchResponse selectedBranch, List<BranchResponse> branches) {
        if (selectedBranch != null && selectedBranch.getCapitalLimit() != null) {
            return selectedBranch.getCapitalLimit();
        }
        BigDecimal sum = branches.stream()
                .map(b -> b.getCapitalLimit() != null ? b.getCapitalLimit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(BigDecimal.ZERO) == 0) {
            return new BigDecimal("2500000000");
        }
        return sum;
    }

    private int clampSize(int rawSize) {
        if (rawSize <= 0) {
            return 10;
        }
        return Math.max(1, Math.min(50, rawSize));
    }

    private YearMonth parseYearMonthSafe(String monthParam) {
        if (monthParam != null && !monthParam.isBlank()) {
            try {
                String trimmed = monthParam.trim();
                if (trimmed.contains("/")) {
                    DateTimeFormatter slashFmt = DateTimeFormatter.ofPattern("M/yyyy");
                    return YearMonth.parse(trimmed, slashFmt);
                }
                if (trimmed.contains("-")) {
                    return YearMonth.parse(trimmed);
                }
                int monthNum = Integer.parseInt(trimmed);
                if (monthNum >= 1 && monthNum <= 12) {
                    return YearMonth.of(LocalDate.now().getYear(), monthNum);
                }
            } catch (Exception e) {
                log.warn("Invalid month parameter '{}', falling back to current YearMonth", monthParam);
            }
        }
        return YearMonth.now();
    }
}

package com.pawnmaster.api.modules.dashboard;

import com.pawnmaster.api.modules.dashboard.dto.DashboardContractPageResponse;
import com.pawnmaster.api.modules.dashboard.dto.DashboardSummaryResponse;

/**
 * Service contract orchestrating Dashboard summary aggregation (Page 0 only)
 * and server-side on-demand (lazy) contract pagination.
 */
public interface DashboardService {

    /**
     * Aggregates real-time Dashboard summary data scoped to a branch and timeframe.
     * Computes KPIs, Cash Flow chart datasets (5 timeframes + legacy aliases),
     * Collateral distribution, Tab counts, and strictly Page 0 of contracts.
     *
     * @param branchId     Target branch ID (null for system-wide / ALL branches if authorized)
     * @param timeframe    Global timeframe filter (defaults to THIS_MONTH if null)
     * @param size         Page size for contract table (default 10, clamped to [1..50])
     * @param legacyMonth  Optional legacy month number (1..12) for backward compatibility
     * @return Fully populated DashboardSummaryResponse
     */
    DashboardSummaryResponse getDashboardSummary(
            Long branchId,
            DashboardTimeframe timeframe,
            int size,
            Integer legacyMonth
    );

    /**
     * Overload supporting legacy month string parameter (e.g. "2026-11", "11/2026", "11").
     */
    DashboardSummaryResponse getDashboardSummary(
            Long branchId,
            DashboardTimeframe timeframe,
            int size,
            String legacyMonth
    );

    /**
     * Convenience overload defaulting legacyMonth to null.
     */
    default DashboardSummaryResponse getDashboardSummary(
            Long branchId,
            DashboardTimeframe timeframe,
            int size
    ) {
        return getDashboardSummary(branchId, timeframe, size, (Integer) null);
    }

    /**
     * Convenience overload defaulting size to 10 and legacyMonth to null.
     */
    default DashboardSummaryResponse getDashboardSummary(
            Long branchId,
            DashboardTimeframe timeframe
    ) {
        return getDashboardSummary(branchId, timeframe, 10, (Integer) null);
    }

    /**
     * Convenience overload defaulting timeframe to THIS_MONTH, size to 10, and legacyMonth to null.
     */
    default DashboardSummaryResponse getDashboardSummary(Long branchId) {
        return getDashboardSummary(branchId, DashboardTimeframe.THIS_MONTH, 10, (Integer) null);
    }

    /**
     * Retrieves an isolated page slice of contracts matching the given filters.
     * Does NOT recalculate KPIs, Cash Flow charts, or Collateral distribution.
     *
     * @param branchId   Target branch ID (null for system-wide if authorized)
     * @param timeframe  Global timeframe filter (defaults to THIS_MONTH if null)
     * @param tab        Status filter tab (defaults to ALL if null)
     * @param keyword    Search keyword matching contract code, customer name, phone, identity, or collateral
     * @param page       0-based page index (clamped to &gt;= 0)
     * @param size       Page size (default 10, clamped to [1..50])
     * @return DashboardContractPageResponse containing the requested slice
     */
    DashboardContractPageResponse getDashboardContractPage(
            Long branchId,
            DashboardTimeframe timeframe,
            DashboardContractTab tab,
            String keyword,
            int page,
            int size
    );

    /**
     * Alias for getDashboardContractPage to ensure compatibility with tasks/plan.md specification.
     */
    default DashboardContractPageResponse getDashboardContractsPage(
            Long branchId,
            DashboardTimeframe timeframe,
            DashboardContractTab tab,
            String keyword,
            int page,
            int size
    ) {
        return getDashboardContractPage(branchId, timeframe, tab, keyword, page, size);
    }

    /**
     * Convenience overload without keyword.
     */
    default DashboardContractPageResponse getDashboardContractPage(
            Long branchId,
            DashboardTimeframe timeframe,
            DashboardContractTab tab,
            int page,
            int size
    ) {
        return getDashboardContractPage(branchId, timeframe, tab, null, page, size);
    }

    /**
     * Convenience overload with default timeframe (THIS_MONTH) and tab (ALL).
     */
    default DashboardContractPageResponse getDashboardContractPage(
            Long branchId,
            int page,
            int size
    ) {
        return getDashboardContractPage(branchId, DashboardTimeframe.THIS_MONTH, DashboardContractTab.ALL, null, page, size);
    }
}

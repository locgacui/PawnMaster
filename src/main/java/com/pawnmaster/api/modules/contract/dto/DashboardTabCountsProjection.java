package com.pawnmaster.api.modules.contract.dto;

/**
 * Spring Data JPA projection for dashboard contract tab counters.
 */
public interface DashboardTabCountsProjection {
    long getAllCount();
    long getPendingCount();
    long getActiveCount();
    long getDueTodayCount();
    long getOverdueCount();
    long getWaitingLiquidationCount();
}

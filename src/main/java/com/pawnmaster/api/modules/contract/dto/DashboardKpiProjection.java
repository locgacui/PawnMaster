package com.pawnmaster.api.modules.contract.dto;

import java.math.BigDecimal;

/**
 * Spring Data JPA projection for aggregated dashboard portfolio/period KPIs.
 */
public interface DashboardKpiProjection {
    long getActiveCount();
    long getPendingCount();
    BigDecimal getDisbursedPrincipal();
    long getOverdueCount();
    long getWaitingLiquidationCount();
    BigDecimal getAtRiskPrincipal();
}

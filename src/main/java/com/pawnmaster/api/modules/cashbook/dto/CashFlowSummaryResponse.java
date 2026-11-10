package com.pawnmaster.api.modules.cashbook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowSummaryResponse {

    private Long branchId;
    private String branchName;

    @Builder.Default
    private BigDecimal currentCashBalance = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal totalCashIn = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal totalCashOut = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal netCashFlow = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal disbursementTotal = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal interestCollectionTotal = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal principalCollectionTotal = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal liquidationIncomeTotal = BigDecimal.ZERO;

    @Builder.Default
    private Long transactionCount = 0L;
}

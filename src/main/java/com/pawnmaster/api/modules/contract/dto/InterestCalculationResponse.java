package com.pawnmaster.api.modules.contract.dto;

import com.pawnmaster.api.modules.contract.InterestType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterestCalculationResponse {

    private Long contractId;
    private String contractCode;
    private BigDecimal principalAmount;
    private BigDecimal remainingPrincipal;
    private InterestType interestType;
    private BigDecimal interestRate;
    private BigDecimal storageFeeRate;
    private LocalDate fromDate;
    private LocalDate toDate;
    private Integer daysCount;

    @Builder.Default
    private BigDecimal interestAmount = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal storageFeeAmount = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal penaltyAmount = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal totalDue = BigDecimal.ZERO;
}

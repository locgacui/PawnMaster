package com.pawnmaster.api.modules.contract.dto;

import com.pawnmaster.api.modules.contract.ContractStatus;
import com.pawnmaster.api.modules.contract.InterestType;
import com.pawnmaster.api.modules.contract.ScheduleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractSettlementPreviewResponse {

    private Long contractId;
    private String contractCode;
    private ContractStatus status;
    private LocalDate asOfDate;

    private BigDecimal remainingPrincipal;
    private InterestType interestType;
    private BigDecimal interestRate;
    private BigDecimal storageFeeRate;
    private Integer paymentCycleDays;

    private LocalDate paidThroughDate;
    private LocalDate nextDueDate;
    private LocalDate maturityDate;

    private Integer currentPeriodNumber;
    private LocalDate currentPeriodFromDate;
    private LocalDate currentPeriodToDate;
    private LocalDate currentPeriodDueDate;
    private ScheduleStatus currentPeriodStatus;
    private BigDecimal currentPeriodExpectedInterest;
    private BigDecimal currentPeriodExpectedStorageFee;
    private BigDecimal currentPeriodExpectedTotal;
    private LocalDate nextPeriodDueDate;

    private Integer openPeriodsCount;
    private Integer paidPeriodsCount;
    private Integer totalPeriodsCount;

    private Integer redemptionElapsedDays;
    private BigDecimal redemptionInterestAmount;
    private BigDecimal redemptionStorageFeeAmount;
    private BigDecimal redemptionPenaltyAmount;
    private BigDecimal redemptionTotalInterestAndFees;
    private BigDecimal redemptionTotalSettlement;
    private Integer redemptionCancelledPeriodsCount;
    private String redemptionAdjustmentSummary;

    @Builder.Default
    private List<ContractPaymentScheduleResponse> schedules = new ArrayList<>();
}


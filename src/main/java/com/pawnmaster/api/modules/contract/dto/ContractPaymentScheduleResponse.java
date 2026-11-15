package com.pawnmaster.api.modules.contract.dto;

import com.pawnmaster.api.modules.contract.ContractPaymentScheduleEntity;
import com.pawnmaster.api.modules.contract.ScheduleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.Hibernate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractPaymentScheduleResponse {

    private Long id;
    private Long contractId;
    private Integer periodNumber;
    private LocalDate fromDate;
    private LocalDate toDate;
    private LocalDate dueDate;
    private Integer daysCount;
    private BigDecimal principalBase;
    private BigDecimal expectedInterestAmount;
    private BigDecimal expectedStorageFeeAmount;
    private BigDecimal expectedTotalAmount;
    private BigDecimal paidInterestAmount;
    private BigDecimal paidStorageFeeAmount;
    private BigDecimal paidPenaltyAmount;
    private BigDecimal paidTotalAmount;
    private ScheduleStatus status;
    private Boolean finalPeriod;
    private LocalDateTime settledAt;
    private Long interestPaymentId;
    private String paymentCode;
    private String adjustmentNote;

    public static ContractPaymentScheduleResponse fromEntity(ContractPaymentScheduleEntity entity) {
        if (entity == null) {
            return null;
        }
        String resolvedPaymentCode = null;
        if (entity.getInterestPayment() != null && Hibernate.isInitialized(entity.getInterestPayment())) {
            resolvedPaymentCode = entity.getInterestPayment().getPaymentCode();
        }
        return ContractPaymentScheduleResponse.builder()
                .id(entity.getId())
                .contractId(entity.getContractId())
                .periodNumber(entity.getPeriodNumber())
                .fromDate(entity.getFromDate())
                .toDate(entity.getToDate())
                .dueDate(entity.getDueDate())
                .daysCount(entity.getDaysCount())
                .principalBase(entity.getPrincipalBase())
                .expectedInterestAmount(entity.getExpectedInterestAmount())
                .expectedStorageFeeAmount(entity.getExpectedStorageFeeAmount())
                .expectedTotalAmount(entity.getExpectedTotalAmount())
                .paidInterestAmount(entity.getPaidInterestAmount())
                .paidStorageFeeAmount(entity.getPaidStorageFeeAmount())
                .paidPenaltyAmount(entity.getPaidPenaltyAmount())
                .paidTotalAmount(entity.getPaidTotalAmount())
                .status(entity.getStatus())
                .finalPeriod(Boolean.TRUE.equals(entity.getFinalPeriod()))
                .settledAt(entity.getSettledAt())
                .interestPaymentId(entity.getInterestPaymentId())
                .paymentCode(resolvedPaymentCode)
                .adjustmentNote(entity.getAdjustmentNote())
                .build();
    }
}


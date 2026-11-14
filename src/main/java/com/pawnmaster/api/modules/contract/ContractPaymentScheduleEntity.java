package com.pawnmaster.api.modules.contract;

import com.pawnmaster.api.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "contract_payment_schedules")
public class ContractPaymentScheduleEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false, foreignKey = @ForeignKey(name = "fk_contract_schedules_contract"))
    private ContractEntity contract;

    @Column(name = "contract_id", insertable = false, updatable = false)
    private Long contractId;

    @Column(name = "period_number", nullable = false)
    private Integer periodNumber;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "days_count", nullable = false)
    private Integer daysCount;

    @Column(name = "principal_base", nullable = false, precision = 18, scale = 2)
    private BigDecimal principalBase;

    @Builder.Default
    @Column(name = "expected_interest_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal expectedInterestAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "expected_storage_fee_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal expectedStorageFeeAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "expected_total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal expectedTotalAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "paid_interest_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal paidInterestAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "paid_storage_fee_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal paidStorageFeeAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "paid_penalty_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal paidPenaltyAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "paid_total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal paidTotalAmount = BigDecimal.ZERO;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ScheduleStatus status = ScheduleStatus.PROJECTED;

    @Builder.Default
    @Column(name = "is_final_period", nullable = false)
    private Boolean finalPeriod = Boolean.FALSE;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interest_payment_id", foreignKey = @ForeignKey(name = "fk_contract_schedules_payment"))
    private InterestPaymentEntity interestPayment;

    @Column(name = "interest_payment_id", insertable = false, updatable = false)
    private Long interestPaymentId;

    @Column(name = "adjustment_note", columnDefinition = "NVARCHAR(500)")
    private String adjustmentNote;

    public Long getContractId() {
        return contract != null ? contract.getId() : contractId;
    }

    public Long getInterestPaymentId() {
        return interestPayment != null ? interestPayment.getId() : interestPaymentId;
    }
}


package com.pawnmaster.api.modules.contract;

import com.pawnmaster.api.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
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
@Table(
        name = "interest_payments",
        indexes = {
                @Index(name = "idx_payments_code", columnList = "payment_code", unique = true),
                @Index(name = "idx_payments_contract", columnList = "contract_id"),
                @Index(name = "idx_payments_date", columnList = "payment_date")
        }
)
public class InterestPaymentEntity extends BaseEntity {

    @Column(name = "payment_code", nullable = false, unique = true, length = 50)
    private String paymentCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false, foreignKey = @ForeignKey(name = "fk_payments_contract"))
    private ContractEntity contract;

    @Column(name = "contract_id", insertable = false, updatable = false)
    private Long contractId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 30)
    private PaymentType paymentType;

    @Column(name = "from_date")
    private LocalDate fromDate;

    @Column(name = "to_date")
    private LocalDate toDate;

    @Column(name = "days_count")
    private Integer daysCount;

    @Column(name = "interest_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal interestAmount = BigDecimal.ZERO;

    @Column(name = "storage_fee_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal storageFeeAmount = BigDecimal.ZERO;

    @Column(name = "penalty_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal penaltyAmount = BigDecimal.ZERO;

    @Column(name = "principal_paid_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal principalPaidAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "payment_date", nullable = false)
    private LocalDateTime paymentDate;

    @Column(name = "payment_method", nullable = false, length = 30)
    @Builder.Default
    private String paymentMethod = "CASH";

    @Column(name = "notes", columnDefinition = "NVARCHAR(500)")
    private String notes;

    public Long getContractId() {
        return contract != null ? contract.getId() : contractId;
    }

    public String getContractCode() {
        return contract != null ? contract.getContractCode() : null;
    }
}

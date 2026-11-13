package com.pawnmaster.api.modules.contract;

import com.pawnmaster.api.common.BaseEntity;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.collateral.CollateralEntity;
import com.pawnmaster.api.modules.customer.CustomerEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
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
        name = "pawn_contracts",
        indexes = {
                @Index(name = "idx_contracts_code", columnList = "contract_code", unique = true),
                @Index(name = "idx_contracts_branch", columnList = "branch_id"),
                @Index(name = "idx_contracts_customer", columnList = "customer_id"),
                @Index(name = "idx_contracts_status", columnList = "status"),
                @Index(name = "idx_contracts_due_date", columnList = "next_interest_due_date, maturity_date")
        }
)
public class ContractEntity extends BaseEntity {

    @Column(name = "contract_code", nullable = false, unique = true, length = 50)
    private String contractCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", foreignKey = @ForeignKey(name = "fk_contracts_branch"))
    private BranchEntity branch;

    @Column(name = "branch_id", insertable = false, updatable = false)
    private Long branchId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_contracts_customer"))
    private CustomerEntity customer;

    @Column(name = "customer_id", insertable = false, updatable = false)
    private Long customerId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collateral_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_contracts_collateral"))
    private CollateralEntity collateral;

    @Column(name = "collateral_id", insertable = false, updatable = false)
    private Long collateralId;

    @Column(name = "principal_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "remaining_principal", nullable = false, precision = 18, scale = 2)
    private BigDecimal remainingPrincipal;

    @Column(name = "applied_ltv_percent", precision = 5, scale = 2)
    private BigDecimal appliedLtvPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_type", nullable = false, length = 30)
    @Builder.Default
    private InterestType interestType = InterestType.DAILY_PER_MILLION;

    @Column(name = "interest_rate", nullable = false, precision = 12, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "storage_fee_rate", precision = 12, scale = 4)
    @Builder.Default
    private BigDecimal storageFeeRate = BigDecimal.ZERO;

    @Column(name = "loan_term_days", nullable = false)
    @Builder.Default
    private Integer loanTermDays = 30;

    @Column(name = "payment_cycle_days", nullable = false)
    @Builder.Default
    private Integer paymentCycleDays = 10;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    @Column(name = "next_interest_due_date", nullable = false)
    private LocalDate nextInterestDueDate;

    @Column(name = "closed_date")
    private LocalDate closedDate;

    @Column(name = "total_interest_paid", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalInterestPaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ContractStatus status = ContractStatus.PENDING_APPROVAL;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approval_notes", columnDefinition = "NVARCHAR(500)")
    private String approvalNotes;

    @Column(name = "notes", columnDefinition = "NVARCHAR(500)")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "disbursement_method", nullable = false, length = 30)
    @Builder.Default
    private DisbursementMethod disbursementMethod = DisbursementMethod.CASH;

    @Column(name = "disbursement_bank_code", length = 20)
    private String disbursementBankCode;

    @Column(name = "disbursement_bank_name", columnDefinition = "NVARCHAR(150)")
    private String disbursementBankName;

    @Column(name = "disbursement_account_number", length = 40)
    private String disbursementAccountNumber;

    @Column(name = "disbursement_account_holder", columnDefinition = "NVARCHAR(150)")
    private String disbursementAccountHolder;

    public Long getBranchId() {
        return branch != null ? branch.getId() : branchId;
    }

    public String getBranchCode() {
        return branch != null ? branch.getBranchCode() : null;
    }

    public String getBranchName() {
        return branch != null ? branch.getBranchName() : null;
    }

    public Long getCustomerId() {
        return customer != null ? customer.getId() : customerId;
    }

    public String getCustomerName() {
        return customer != null ? customer.getFullName() : null;
    }

    public Long getCollateralId() {
        return collateral != null ? collateral.getId() : collateralId;
    }

    public String getCollateralName() {
        return collateral != null ? collateral.getName() : null;
    }
}

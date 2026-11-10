package com.pawnmaster.api.modules.cashbook;

import com.pawnmaster.api.common.BaseEntity;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.contract.ContractEntity;
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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "cashbook_transactions",
        indexes = {
                @Index(name = "idx_cashbook_code", columnList = "transaction_code", unique = true),
                @Index(name = "idx_cashbook_branch", columnList = "branch_id"),
                @Index(name = "idx_cashbook_type_date", columnList = "transaction_type, transaction_date"),
                @Index(name = "idx_cashbook_contract", columnList = "contract_id")
        }
)
public class CashbookEntity extends BaseEntity {

    @Column(name = "transaction_code", nullable = false, unique = true, length = 50)
    private String transactionCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", foreignKey = @ForeignKey(name = "fk_cashbook_branch"))
    private BranchEntity branch;

    @Column(name = "branch_id", insertable = false, updatable = false)
    private Long branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private TransactionCategory category;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", precision = 18, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "payment_method", nullable = false, length = 30)
    @Builder.Default
    private String paymentMethod = "CASH";

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", foreignKey = @ForeignKey(name = "fk_cashbook_contract"))
    private ContractEntity contract;

    @Column(name = "contract_id", insertable = false, updatable = false)
    private Long contractId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", foreignKey = @ForeignKey(name = "fk_cashbook_customer"))
    private CustomerEntity customer;

    @Column(name = "customer_id", insertable = false, updatable = false)
    private Long customerId;

    @Column(name = "payer_or_receiver_name", columnDefinition = "NVARCHAR(150)")
    private String payerOrReceiverName;

    @Column(name = "description", columnDefinition = "NVARCHAR(500)")
    private String description;

    @Column(name = "notes", columnDefinition = "NVARCHAR(500)")
    private String notes;

    @Column(name = "performed_by")
    private Long performedBy;

    public Long getBranchId() {
        return branch != null ? branch.getId() : branchId;
    }

    public String getBranchName() {
        return branch != null ? branch.getBranchName() : null;
    }

    public Long getContractId() {
        return contract != null ? contract.getId() : contractId;
    }

    public String getContractCode() {
        return contract != null ? contract.getContractCode() : null;
    }

    public Long getCustomerId() {
        return customer != null ? customer.getId() : customerId;
    }

    public String getCustomerName() {
        return customer != null ? customer.getFullName() : null;
    }
}

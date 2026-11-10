package com.pawnmaster.api.modules.cashbook.dto;

import com.pawnmaster.api.modules.cashbook.CashbookEntity;
import com.pawnmaster.api.modules.cashbook.TransactionCategory;
import com.pawnmaster.api.modules.cashbook.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashbookResponse {

    private Long id;
    private String transactionCode;
    private Long branchId;
    private String branchCode;
    private String branchName;
    private TransactionType transactionType;
    private TransactionCategory category;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String paymentMethod;
    private LocalDateTime transactionDate;
    private Long contractId;
    private String contractCode;
    private Long customerId;
    private String customerName;
    private String payerOrReceiverName;
    private String description;
    private String notes;
    private Long performedBy;
    private LocalDateTime createdAt;

    public static CashbookResponse fromEntity(CashbookEntity entity) {
        if (entity == null) {
            return null;
        }

        Long bId = null;
        String bCode = null;
        String bName = null;
        if (entity.getBranch() != null) {
            bId = entity.getBranch().getId();
            bCode = entity.getBranch().getBranchCode();
            bName = entity.getBranch().getBranchName();
        }

        Long cntId = null;
        String cntCode = null;
        if (entity.getContract() != null) {
            cntId = entity.getContract().getId();
            cntCode = entity.getContract().getContractCode();
        }

        Long cstId = null;
        String cstName = null;
        if (entity.getCustomer() != null) {
            cstId = entity.getCustomer().getId();
            cstName = entity.getCustomer().getFullName();
        }

        String noteVal = entity.getNotes() != null ? entity.getNotes() : entity.getDescription();

        return CashbookResponse.builder()
                .id(entity.getId())
                .transactionCode(entity.getTransactionCode())
                .branchId(bId)
                .branchCode(bCode)
                .branchName(bName)
                .transactionType(entity.getTransactionType())
                .category(entity.getCategory())
                .amount(entity.getAmount())
                .balanceAfter(entity.getBalanceAfter())
                .paymentMethod(entity.getPaymentMethod())
                .transactionDate(entity.getTransactionDate())
                .contractId(cntId)
                .contractCode(cntCode)
                .customerId(cstId)
                .customerName(cstName)
                .payerOrReceiverName(entity.getPayerOrReceiverName())
                .description(entity.getDescription())
                .notes(noteVal)
                .performedBy(entity.getPerformedBy())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

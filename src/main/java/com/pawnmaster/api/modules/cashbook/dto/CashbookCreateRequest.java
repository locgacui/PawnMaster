package com.pawnmaster.api.modules.cashbook.dto;

import com.pawnmaster.api.modules.cashbook.TransactionCategory;
import com.pawnmaster.api.modules.cashbook.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
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
public class CashbookCreateRequest {

    private Long branchId;

    @NotNull(message = "Loại giao dịch không được để trống")
    private TransactionType transactionType;

    @NotNull(message = "Danh mục giao dịch không được để trống")
    private TransactionCategory category;

    @NotNull(message = "Số tiền không được để trống")
    @DecimalMin(value = "0.01", message = "Số tiền giao dịch phải lớn hơn 0")
    private BigDecimal amount;

    @Builder.Default
    private String paymentMethod = "CASH";

    private Long contractId;
    private Long customerId;
    private String payerOrReceiverName;
    private String description;
    private String notes;
    private Long performedBy;
    private LocalDateTime transactionDate;
}

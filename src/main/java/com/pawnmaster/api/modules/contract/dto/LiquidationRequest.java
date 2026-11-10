package com.pawnmaster.api.modules.contract.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiquidationRequest {

    @NotNull(message = "Số tiền thanh lý không được để trống")
    @DecimalMin(value = "0.01", message = "Số tiền thanh lý phải lớn hơn 0")
    private BigDecimal liquidationAmount;

    private String buyerName;
    private String notes;

    @Builder.Default
    private String paymentMethod = "CASH";
}

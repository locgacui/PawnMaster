package com.pawnmaster.api.modules.contract.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractRedeemRequest {

    private BigDecimal accruedInterest;
    private BigDecimal storageFee;
    private BigDecimal penaltyAmount;
    private String notes;

    @Builder.Default
    private String paymentMethod = "CASH";
}

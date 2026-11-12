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
public class InterestCollectionRequest {

    private Long collectionBranchId;

    @Builder.Default
    private BigDecimal interestAmount = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal storageFeeAmount = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal penaltyAmount = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal principalPaidAmount = BigDecimal.ZERO;

    @Builder.Default
    private String paymentMethod = "CASH";

    private String notes;
}

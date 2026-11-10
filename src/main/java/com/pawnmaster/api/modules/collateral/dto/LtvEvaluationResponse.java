package com.pawnmaster.api.modules.collateral.dto;

import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LtvEvaluationResponse {

    private CustomerOccupationGroup occupationGroup;
    private CollateralCategory category;
    private BigDecimal appraisedValue;
    private BigDecimal maxLtvPercent;
    private BigDecimal maxAllowedLoanAmount;
    private BigDecimal requestedLoanAmount;
    private boolean eligible;
    private String eligibleMessage;
}

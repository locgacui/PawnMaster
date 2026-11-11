package com.pawnmaster.api.modules.contract.dto;

import com.pawnmaster.api.modules.collateral.CollateralCategory;

import java.math.BigDecimal;

/**
 * Spring Data JPA projection for collateral category distribution aggregation.
 */
public interface CollateralCategoryGroupProjection {
    CollateralCategory getCategory();
    long getContractCount();
    BigDecimal getTotalAppraisedValue();
}

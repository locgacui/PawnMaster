package com.pawnmaster.api.modules.collateral;

import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.VerificationDocumentType;

import java.math.BigDecimal;
import java.util.List;

public interface LtvPolicyService {

    BigDecimal getMaxLtvPercent(CustomerOccupationGroup group, CollateralCategory category);

    BigDecimal getMaxLtvRatio(CustomerOccupationGroup group, CollateralCategory category);

    BigDecimal calculateMaxLoanAmount(CustomerOccupationGroup group, CollateralCategory category,
            BigDecimal appraisedValue);

    void validateLoanCeiling(CustomerOccupationGroup group, CollateralCategory category, BigDecimal appraisedValue,
            BigDecimal loanAmount);

    void validateLoanRequest(
            CustomerOccupationGroup group,
            CollateralCategory category,
            BigDecimal appraisedValue,
            BigDecimal loanAmount,
            List<String> docUrls,
            VerificationDocumentType docType);

    boolean isDocumentValidForGroup(CustomerOccupationGroup group, VerificationDocumentType docType);

    java.util.Set<VerificationDocumentType> getAllowedDocuments(CustomerOccupationGroup group);

    void validateVerificationDocuments(CustomerOccupationGroup group, VerificationDocumentType docType,
            java.util.Collection<String> docUrls);
}

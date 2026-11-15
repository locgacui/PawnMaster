package com.pawnmaster.api.modules.collateral;

import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;

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
            String docCode);

    default void validateLoanRequestByDocCode(
            CustomerOccupationGroup group,
            CollateralCategory category,
            BigDecimal appraisedValue,
            BigDecimal loanAmount,
            List<String> docUrls,
            String docCode) {
        validateLoanRequest(group, category, appraisedValue, loanAmount, docUrls, docCode);
    }

    boolean isDocumentValidForGroup(CustomerOccupationGroup group, String docCode);

    default boolean isDocumentCodeValidForGroup(CustomerOccupationGroup group, String docCode) {
        return isDocumentValidForGroup(group, docCode);
    }

    List<String> getAllowedDocumentCodes(CustomerOccupationGroup group);

    void validateVerificationDocuments(CustomerOccupationGroup group, String docCode,
            java.util.Collection<String> docUrls);

    default void validateVerificationDocumentsByCode(CustomerOccupationGroup group, String docCode,
            java.util.Collection<String> docUrls) {
        validateVerificationDocuments(group, docCode, docUrls);
    }
}

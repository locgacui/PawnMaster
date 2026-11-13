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

    default void validateLoanRequestByDocCode(
            CustomerOccupationGroup group,
            CollateralCategory category,
            BigDecimal appraisedValue,
            BigDecimal loanAmount,
            List<String> docUrls,
            String docCode) {
        validateLoanRequest(group, category, appraisedValue, loanAmount, docUrls,
                VerificationDocumentType.fromCodeOrNull(docCode));
    }

    boolean isDocumentValidForGroup(CustomerOccupationGroup group, VerificationDocumentType docType);

    default boolean isDocumentCodeValidForGroup(CustomerOccupationGroup group, String docCode) {
        VerificationDocumentType parsed = VerificationDocumentType.fromCodeOrNull(docCode);
        return parsed != null && isDocumentValidForGroup(group, parsed);
    }

    java.util.Set<VerificationDocumentType> getAllowedDocuments(CustomerOccupationGroup group);

    default List<String> getAllowedDocumentCodes(CustomerOccupationGroup group) {
        return getAllowedDocuments(group).stream().map(Enum::name).toList();
    }

    void validateVerificationDocuments(CustomerOccupationGroup group, VerificationDocumentType docType,
            java.util.Collection<String> docUrls);

    default void validateVerificationDocumentsByCode(CustomerOccupationGroup group, String docCode,
            java.util.Collection<String> docUrls) {
        validateVerificationDocuments(group, VerificationDocumentType.fromCodeOrNull(docCode), docUrls);
    }
}

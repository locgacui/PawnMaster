package com.pawnmaster.api.modules.customer;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@Component
public class VerificationDocumentPolicy {

    private static final Map<CustomerOccupationGroup, Set<VerificationDocumentType>> VALID_DOCS = new EnumMap<>(CustomerOccupationGroup.class);

    static {
        // 1. OFFICE_WORKER
        VALID_DOCS.put(CustomerOccupationGroup.OFFICE_WORKER, Set.of(
                VerificationDocumentType.PAYSLIP_BANK_STATEMENT,
                VerificationDocumentType.LABOR_CONTRACT,
                VerificationDocumentType.SOCIAL_INSURANCE_VSSID
        ));

        // 2. FACTORY_WORKER
        VALID_DOCS.put(CustomerOccupationGroup.FACTORY_WORKER, Set.of(
                VerificationDocumentType.PAYSLIP_BANK_STATEMENT,
                VerificationDocumentType.WORKER_ID_OR_CONTRACT,
                VerificationDocumentType.SOCIAL_INSURANCE_VSSID
        ));

        // 3. TECH_DRIVER
        VALID_DOCS.put(CustomerOccupationGroup.TECH_DRIVER, Set.of(
                VerificationDocumentType.DRIVER_APP_INCOME_SCREENSHOT,
                VerificationDocumentType.RESIDENCE_CONFIRMATION_CT07,
                VerificationDocumentType.RENTAL_CONTRACT
        ));

        // 4. SMALL_MERCHANT
        VALID_DOCS.put(CustomerOccupationGroup.SMALL_MERCHANT, Set.of(
                VerificationDocumentType.BUSINESS_LICENSE_OR_STALL_LEASE,
                VerificationDocumentType.SHOP_PHOTO_AND_CASHFLOW
        ));

        // 5. ONLINE_SELLER
        VALID_DOCS.put(CustomerOccupationGroup.ONLINE_SELLER, Set.of(
                VerificationDocumentType.ECOMMERCE_REVENUE_SCREENSHOT,
                VerificationDocumentType.BANK_COD_STATEMENT
        ));

        // 6. FREELANCER
        VALID_DOCS.put(CustomerOccupationGroup.FREELANCER, Set.of(
                VerificationDocumentType.RESIDENCE_CONFIRMATION_CT07,
                VerificationDocumentType.RENTAL_CONTRACT,
                VerificationDocumentType.UTILITY_BILL
        ));
    }

    public boolean isDocumentValidForGroup(CustomerOccupationGroup group, VerificationDocumentType docType) {
        if (group == null || docType == null) {
            return false;
        }
        Set<VerificationDocumentType> allowed = VALID_DOCS.get(group);
        return allowed != null && allowed.contains(docType);
    }

    public Set<VerificationDocumentType> getAllowedDocuments(CustomerOccupationGroup group) {
        if (group == null) {
            return Collections.emptySet();
        }
        return VALID_DOCS.getOrDefault(group, Collections.emptySet());
    }

    public void validateVerificationDocuments(CustomerOccupationGroup group, VerificationDocumentType docType, Collection<String> docUrls) {
        if (docUrls == null || docUrls.isEmpty() || docType == null) {
            throw new BusinessException(ErrorCode.MISSING_VERIFICATION_DOCUMENTS,
                    "Bắt buộc phải tải lên hình ảnh chứng từ xác minh thu nhập/cư trú theo nhóm nghề nghiệp");
        }
        boolean hasNonEmptyUrl = docUrls.stream().anyMatch(url -> url != null && !url.trim().isEmpty());
        if (!hasNonEmptyUrl) {
            throw new BusinessException(ErrorCode.MISSING_VERIFICATION_DOCUMENTS,
                    "Bắt buộc phải tải lên hình ảnh chứng từ xác minh thu nhập/cư trú theo nhóm nghề nghiệp");
        }
        if (!isDocumentValidForGroup(group, docType)) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_DOCUMENT,
                    "Loại chứng từ " + docType.getDescription() + " không phù hợp với nhóm " + (group != null ? group.getDescription() : ""));
        }
    }
}


package com.pawnmaster.api.modules.collateral;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.VerificationDocumentType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class LtvMatrixPolicy {

    private static final Map<CustomerOccupationGroup, Map<CollateralCategory, BigDecimal>> MATRIX = new EnumMap<>(CustomerOccupationGroup.class);
    private static final Map<CustomerOccupationGroup, Set<VerificationDocumentType>> VALID_DOCS = new EnumMap<>(CustomerOccupationGroup.class);

    static {
        // 1. OFFICE_WORKER
        Map<CollateralCategory, BigDecimal> office = new EnumMap<>(CollateralCategory.class);
        office.put(CollateralCategory.GOLD_JEWELRY, new BigDecimal("0.92"));
        office.put(CollateralCategory.MOTORBIKE, new BigDecimal("0.90"));
        office.put(CollateralCategory.CAR, new BigDecimal("0.85"));
        office.put(CollateralCategory.PHONE, new BigDecimal("0.80"));
        office.put(CollateralCategory.LAPTOP, new BigDecimal("0.80"));
        office.put(CollateralCategory.WATCH, new BigDecimal("0.75"));
        office.put(CollateralCategory.REAL_ESTATE, new BigDecimal("0.75"));
        office.put(CollateralCategory.OTHER, new BigDecimal("0.70"));
        MATRIX.put(CustomerOccupationGroup.OFFICE_WORKER, Collections.unmodifiableMap(office));

        VALID_DOCS.put(CustomerOccupationGroup.OFFICE_WORKER, Set.of(
                VerificationDocumentType.PAYSLIP_BANK_STATEMENT,
                VerificationDocumentType.LABOR_CONTRACT,
                VerificationDocumentType.SOCIAL_INSURANCE_VSSID
        ));

        // 2. FACTORY_WORKER
        Map<CollateralCategory, BigDecimal> factory = new EnumMap<>(CollateralCategory.class);
        factory.put(CollateralCategory.GOLD_JEWELRY, new BigDecimal("0.90"));
        factory.put(CollateralCategory.MOTORBIKE, new BigDecimal("0.80"));
        factory.put(CollateralCategory.CAR, new BigDecimal("0.75"));
        factory.put(CollateralCategory.PHONE, new BigDecimal("0.75"));
        factory.put(CollateralCategory.LAPTOP, new BigDecimal("0.75"));
        factory.put(CollateralCategory.WATCH, new BigDecimal("0.70"));
        factory.put(CollateralCategory.REAL_ESTATE, new BigDecimal("0.70"));
        factory.put(CollateralCategory.OTHER, new BigDecimal("0.65"));
        MATRIX.put(CustomerOccupationGroup.FACTORY_WORKER, Collections.unmodifiableMap(factory));

        VALID_DOCS.put(CustomerOccupationGroup.FACTORY_WORKER, Set.of(
                VerificationDocumentType.PAYSLIP_BANK_STATEMENT,
                VerificationDocumentType.WORKER_ID_OR_CONTRACT,
                VerificationDocumentType.SOCIAL_INSURANCE_VSSID
        ));

        // 3. TECH_DRIVER
        Map<CollateralCategory, BigDecimal> driver = new EnumMap<>(CollateralCategory.class);
        driver.put(CollateralCategory.GOLD_JEWELRY, new BigDecimal("0.88"));
        driver.put(CollateralCategory.MOTORBIKE, new BigDecimal("0.70"));
        driver.put(CollateralCategory.CAR, new BigDecimal("0.70"));
        driver.put(CollateralCategory.PHONE, new BigDecimal("0.70"));
        driver.put(CollateralCategory.LAPTOP, new BigDecimal("0.70"));
        driver.put(CollateralCategory.WATCH, new BigDecimal("0.65"));
        driver.put(CollateralCategory.REAL_ESTATE, new BigDecimal("0.65"));
        driver.put(CollateralCategory.OTHER, new BigDecimal("0.60"));
        MATRIX.put(CustomerOccupationGroup.TECH_DRIVER, Collections.unmodifiableMap(driver));

        VALID_DOCS.put(CustomerOccupationGroup.TECH_DRIVER, Set.of(
                VerificationDocumentType.DRIVER_APP_INCOME_SCREENSHOT,
                VerificationDocumentType.RESIDENCE_CONFIRMATION_CT07,
                VerificationDocumentType.RENTAL_CONTRACT
        ));

        // 4. SMALL_MERCHANT
        Map<CollateralCategory, BigDecimal> merchant = new EnumMap<>(CollateralCategory.class);
        merchant.put(CollateralCategory.GOLD_JEWELRY, new BigDecimal("0.90"));
        merchant.put(CollateralCategory.MOTORBIKE, new BigDecimal("0.80"));
        merchant.put(CollateralCategory.CAR, new BigDecimal("0.80"));
        merchant.put(CollateralCategory.PHONE, new BigDecimal("0.75"));
        merchant.put(CollateralCategory.LAPTOP, new BigDecimal("0.75"));
        merchant.put(CollateralCategory.WATCH, new BigDecimal("0.75"));
        merchant.put(CollateralCategory.REAL_ESTATE, new BigDecimal("0.70"));
        merchant.put(CollateralCategory.OTHER, new BigDecimal("0.65"));
        MATRIX.put(CustomerOccupationGroup.SMALL_MERCHANT, Collections.unmodifiableMap(merchant));

        VALID_DOCS.put(CustomerOccupationGroup.SMALL_MERCHANT, Set.of(
                VerificationDocumentType.BUSINESS_LICENSE_OR_STALL_LEASE,
                VerificationDocumentType.SHOP_PHOTO_AND_CASHFLOW
        ));

        // 5. ONLINE_SELLER
        Map<CollateralCategory, BigDecimal> seller = new EnumMap<>(CollateralCategory.class);
        seller.put(CollateralCategory.GOLD_JEWELRY, new BigDecimal("0.88"));
        seller.put(CollateralCategory.MOTORBIKE, new BigDecimal("0.75"));
        seller.put(CollateralCategory.CAR, new BigDecimal("0.75"));
        seller.put(CollateralCategory.PHONE, new BigDecimal("0.70"));
        seller.put(CollateralCategory.LAPTOP, new BigDecimal("0.70"));
        seller.put(CollateralCategory.WATCH, new BigDecimal("0.68"));
        seller.put(CollateralCategory.REAL_ESTATE, new BigDecimal("0.65"));
        seller.put(CollateralCategory.OTHER, new BigDecimal("0.60"));
        MATRIX.put(CustomerOccupationGroup.ONLINE_SELLER, Collections.unmodifiableMap(seller));

        VALID_DOCS.put(CustomerOccupationGroup.ONLINE_SELLER, Set.of(
                VerificationDocumentType.ECOMMERCE_REVENUE_SCREENSHOT,
                VerificationDocumentType.BANK_COD_STATEMENT
        ));

        // 6. FREELANCER
        Map<CollateralCategory, BigDecimal> freelancer = new EnumMap<>(CollateralCategory.class);
        freelancer.put(CollateralCategory.GOLD_JEWELRY, new BigDecimal("0.85"));
        freelancer.put(CollateralCategory.MOTORBIKE, new BigDecimal("0.65"));
        freelancer.put(CollateralCategory.CAR, new BigDecimal("0.65"));
        freelancer.put(CollateralCategory.PHONE, new BigDecimal("0.60"));
        freelancer.put(CollateralCategory.LAPTOP, new BigDecimal("0.60"));
        freelancer.put(CollateralCategory.WATCH, new BigDecimal("0.55"));
        freelancer.put(CollateralCategory.REAL_ESTATE, new BigDecimal("0.55"));
        freelancer.put(CollateralCategory.OTHER, new BigDecimal("0.50"));
        MATRIX.put(CustomerOccupationGroup.FREELANCER, Collections.unmodifiableMap(freelancer));

        VALID_DOCS.put(CustomerOccupationGroup.FREELANCER, Set.of(
                VerificationDocumentType.RESIDENCE_CONFIRMATION_CT07,
                VerificationDocumentType.RENTAL_CONTRACT,
                VerificationDocumentType.UTILITY_BILL
        ));
    }

    public BigDecimal getMaxLtvPercent(CustomerOccupationGroup group, CollateralCategory category) {
        if (group == null || category == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Nhóm khách hàng và loại tài sản không được để trống");
        }
        Map<CollateralCategory, BigDecimal> row = MATRIX.get(group);
        if (row == null || !row.containsKey(category)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Không tìm thấy cấu hình LTV cho nhóm: " + group + " và loại: " + category);
        }
        return row.get(category);
    }

    public BigDecimal getMaxLtvRatio(CustomerOccupationGroup group, CollateralCategory category) {
        return getMaxLtvPercent(group, category);
    }

    public BigDecimal calculateMaxLoanAmount(CustomerOccupationGroup group, CollateralCategory category, BigDecimal appraisedValue) {
        if (appraisedValue == null || appraisedValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Giá trị định giá phải lớn hơn 0");
        }
        BigDecimal ltvRatio = getMaxLtvPercent(group, category);
        return appraisedValue.multiply(ltvRatio).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isDocumentValidForGroup(CustomerOccupationGroup group, VerificationDocumentType docType) {
        if (group == null || docType == null) return false;
        Set<VerificationDocumentType> allowed = VALID_DOCS.get(group);
        return allowed != null && allowed.contains(docType);
    }

    public Set<VerificationDocumentType> getAllowedDocuments(CustomerOccupationGroup group) {
        if (group == null) return Collections.emptySet();
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

    public void validateLoanRequest(
            CustomerOccupationGroup group,
            CollateralCategory category,
            BigDecimal appraisedValue,
            BigDecimal loanAmount,
            List<String> docUrls,
            VerificationDocumentType docType
    ) {
        validateVerificationDocuments(group, docType, docUrls);

        if (appraisedValue == null || loanAmount == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Giá trị thẩm định và số tiền vay không được để trống");
        }
        if (appraisedValue.compareTo(BigDecimal.ZERO) <= 0 || loanAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Giá trị thẩm định và số tiền vay phải lớn hơn 0");
        }

        // Absolute Ceiling: Cannot exceed 100% of appraised value
        if (loanAmount.compareTo(appraisedValue) > 0) {
            throw new BusinessException(ErrorCode.EXCEEDS_MAX_LTV,
                    "Số tiền vay (" + loanAmount + " đ) vượt quá trần 100% giá trị định giá tài sản (" + appraisedValue + " đ)");
        }

        // Category-Specific LTV Matrix Limit
        BigDecimal maxAllowedLoan = calculateMaxLoanAmount(group, category, appraisedValue);
        if (loanAmount.compareTo(maxAllowedLoan) > 0) {
            BigDecimal ltvPercent = getMaxLtvPercent(group, category).multiply(BigDecimal.valueOf(100));
            throw new BusinessException(ErrorCode.EXCEEDS_MAX_LTV,
                    String.format("Số tiền vay (%s đ) vượt quá hạn mức tối đa cho phép (%s đ - %s%% định giá)",
                            loanAmount, maxAllowedLoan, ltvPercent.stripTrailingZeros().toPlainString()));
        }
    }
}

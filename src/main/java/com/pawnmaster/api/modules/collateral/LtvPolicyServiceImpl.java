package com.pawnmaster.api.modules.collateral;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.VerificationDocumentPolicy;
import com.pawnmaster.api.modules.customer.VerificationDocumentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class LtvPolicyServiceImpl implements LtvPolicyService {

    private final LtvPolicyRepository ltvPolicyRepository;
    private final VerificationDocumentPolicy verificationDocumentPolicy;

    @Autowired
    public LtvPolicyServiceImpl(LtvPolicyRepository ltvPolicyRepository,
            VerificationDocumentPolicy verificationDocumentPolicy) {
        this.ltvPolicyRepository = ltvPolicyRepository;
        this.verificationDocumentPolicy = verificationDocumentPolicy != null ? verificationDocumentPolicy
                : new VerificationDocumentPolicy();
    }

    public LtvPolicyServiceImpl(LtvPolicyRepository ltvPolicyRepository) {
        this(ltvPolicyRepository, new VerificationDocumentPolicy());
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getMaxLtvPercent(CustomerOccupationGroup group, CollateralCategory category) {
        if (group == null || category == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Nhóm khách hàng và loại tài sản không được để trống");
        }
        BigDecimal percent = ltvPolicyRepository.findByOccupationGroupAndCollateralCategoryAndDeletedFalse(group, category)
                .map(LtvPolicyEntity::getMaxLtvPercent)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST,
                        "Không tìm thấy cấu hình LTV cho nhóm: " + group + " và loại: " + category));

        if (percent == null || percent.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Cấu hình tỷ lệ LTV không hợp lệ (phải lớn hơn 0): " + percent);
        }
        if (percent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Tỷ lệ LTV cấu hình vượt quá trần tối đa 100%: " + percent);
        }
        return percent;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getMaxLtvRatio(CustomerOccupationGroup group, CollateralCategory category) {
        BigDecimal val = getMaxLtvPercent(group, category);
        if (val != null && val.compareTo(BigDecimal.ONE) > 0) {
            val = val.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        }
        if (val != null && val.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Tỷ lệ LTV cấu hình vượt quá trần tối đa 100%: " + val);
        }
        return val;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal calculateMaxLoanAmount(CustomerOccupationGroup group, CollateralCategory category,
            BigDecimal appraisedValue) {
        if (appraisedValue == null || appraisedValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Giá trị định giá phải lớn hơn 0");
        }
        BigDecimal ltvRatio = getMaxLtvRatio(group, category);
        return appraisedValue.multiply(ltvRatio).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(readOnly = true)
    public void validateLoanCeiling(CustomerOccupationGroup group, CollateralCategory category,
            BigDecimal appraisedValue, BigDecimal loanAmount) {
        if (appraisedValue == null || loanAmount == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Giá trị thẩm định và số tiền vay không được để trống");
        }
        if (appraisedValue.compareTo(BigDecimal.ZERO) <= 0 || loanAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Giá trị thẩm định và số tiền vay phải lớn hơn 0");
        }

        // Absolute Ceiling: Cannot exceed 100% of appraised value
        if (loanAmount.compareTo(appraisedValue) > 0) {
            throw new BusinessException(ErrorCode.EXCEEDS_MAX_LTV,
                    "Số tiền vay (" + loanAmount + " đ) vượt quá trần 100% giá trị định giá tài sản (" + appraisedValue
                            + " đ)");
        }

        // Category-Specific LTV Matrix Limit
        BigDecimal maxAllowedLoan = calculateMaxLoanAmount(group, category, appraisedValue);
        if (loanAmount.compareTo(maxAllowedLoan) > 0) {
            BigDecimal ltvPercent = getMaxLtvRatio(group, category).multiply(BigDecimal.valueOf(100));
            throw new BusinessException(ErrorCode.EXCEEDS_MAX_LTV,
                    String.format("Số tiền vay (%s đ) vượt quá hạn mức tối đa cho phép (%s đ - %s%% định giá)",
                            loanAmount, maxAllowedLoan, ltvPercent.stripTrailingZeros().toPlainString()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void validateLoanRequest(
            CustomerOccupationGroup group,
            CollateralCategory category,
            BigDecimal appraisedValue,
            BigDecimal loanAmount,
            List<String> docUrls,
            VerificationDocumentType docType) {
        if (verificationDocumentPolicy != null) {
            verificationDocumentPolicy.validateVerificationDocuments(group, docType, docUrls);
        }
        validateLoanCeiling(group, category, appraisedValue, loanAmount);
    }

    @Override
    public boolean isDocumentValidForGroup(CustomerOccupationGroup group, VerificationDocumentType docType) {
        return verificationDocumentPolicy != null && verificationDocumentPolicy.isDocumentValidForGroup(group, docType);
    }

    @Override
    public java.util.Set<VerificationDocumentType> getAllowedDocuments(CustomerOccupationGroup group) {
        return verificationDocumentPolicy != null ? verificationDocumentPolicy.getAllowedDocuments(group)
                : java.util.Collections.emptySet();
    }

    @Override
    public void validateVerificationDocuments(CustomerOccupationGroup group, VerificationDocumentType docType,
            java.util.Collection<String> docUrls) {
        if (verificationDocumentPolicy != null) {
            verificationDocumentPolicy.validateVerificationDocuments(group, docType, docUrls);
        }
    }
}

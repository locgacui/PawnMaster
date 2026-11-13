package com.pawnmaster.api.modules.collateral;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.VerificationDocumentPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;

@Service
public class LtvPolicyServiceImpl implements LtvPolicyService {

    private final LtvPolicyRepository ltvPolicyRepository;
    private final VerificationDocumentPolicy verificationDocumentPolicy;

    @Autowired
    public LtvPolicyServiceImpl(LtvPolicyRepository ltvPolicyRepository,
            @Autowired(required = false) VerificationDocumentPolicy verificationDocumentPolicy) {
        this.ltvPolicyRepository = ltvPolicyRepository;
        this.verificationDocumentPolicy = verificationDocumentPolicy;
    }

    public LtvPolicyServiceImpl(LtvPolicyRepository ltvPolicyRepository) {
        this(ltvPolicyRepository, null);
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
            String docCode) {
        if (verificationDocumentPolicy != null) {
            verificationDocumentPolicy.validateVerificationDocumentsByCode(group, docCode, docUrls);
        }
        validateLoanCeiling(group, category, appraisedValue, loanAmount);
    }

    @Override
    @Transactional(readOnly = true)
    public void validateLoanRequestByDocCode(
            CustomerOccupationGroup group,
            CollateralCategory category,
            BigDecimal appraisedValue,
            BigDecimal loanAmount,
            List<String> docUrls,
            String docCode) {
        validateLoanRequest(group, category, appraisedValue, loanAmount, docUrls, docCode);
    }

    @Override
    public boolean isDocumentValidForGroup(CustomerOccupationGroup group, String docCode) {
        return verificationDocumentPolicy != null && verificationDocumentPolicy.isDocumentCodeValidForGroup(group, docCode);
    }

    @Override
    public boolean isDocumentCodeValidForGroup(CustomerOccupationGroup group, String docCode) {
        return isDocumentValidForGroup(group, docCode);
    }

    @Override
    public List<String> getAllowedDocumentCodes(CustomerOccupationGroup group) {
        return verificationDocumentPolicy != null ? verificationDocumentPolicy.getAllowedDocumentCodes(group)
                : Collections.emptyList();
    }

    @Override
    public void validateVerificationDocuments(CustomerOccupationGroup group, String docCode,
            java.util.Collection<String> docUrls) {
        if (verificationDocumentPolicy != null) {
            verificationDocumentPolicy.validateVerificationDocumentsByCode(group, docCode, docUrls);
        }
    }

    @Override
    public void validateVerificationDocumentsByCode(CustomerOccupationGroup group, String docCode,
            java.util.Collection<String> docUrls) {
        validateVerificationDocuments(group, docCode, docUrls);
    }
}

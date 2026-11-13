package com.pawnmaster.api.modules.collateral.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.VerificationDocumentPolicy;
import com.pawnmaster.api.modules.customer.VerificationDocumentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LtvEvaluationRequest {

    @NotNull(message = "Nhóm nghề nghiệp không được để trống")
    private CustomerOccupationGroup occupationGroup;

    @NotNull(message = "Loại tài sản không được để trống")
    private CollateralCategory category;

    @NotNull(message = "Giá trị thẩm định không được để trống")
    @Positive(message = "Giá trị thẩm định phải > 0")
    private BigDecimal appraisedValue;

    private BigDecimal requestedLoanAmount;
    private String docType;
    private List<String> docUrls;

    @JsonSetter("docType")
    public void setDocType(String docType) {
        this.docType = VerificationDocumentPolicy.normalizeDocCode(docType);
    }

    public void setDocType(VerificationDocumentType docType) {
        this.docType = docType != null ? docType.name() : null;
    }

    public static class LtvEvaluationRequestBuilder {
        public LtvEvaluationRequestBuilder docType(String docType) {
            this.docType = VerificationDocumentPolicy.normalizeDocCode(docType);
            return this;
        }

        public LtvEvaluationRequestBuilder docType(VerificationDocumentType docType) {
            this.docType = docType != null ? docType.name() : null;
            return this;
        }
    }
}

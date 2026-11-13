package com.pawnmaster.api.modules.customer.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.CustomerStatus;
import com.pawnmaster.api.modules.customer.VerificationDocumentPolicy;
import com.pawnmaster.api.modules.customer.VerificationDocumentType;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerUpdateRequest {

    @Size(max = 150)
    private String fullName;

    private LocalDate identityIssueDate;
    private String identityIssuePlace;
    private String phoneNumber;
    private String email;
    private LocalDate dateOfBirth;
    private String gender;

    @Size(max = 20)
    private String provinceCode;

    @Size(max = 20)
    private String districtCode;

    @Size(max = 20)
    private String wardCode;

    @Size(max = 255)
    private String addressDetail;

    private String address;
    private String permanentAddress;

    private CustomerOccupationGroup occupationGroup;

    @PositiveOrZero(message = "Thu nhập hàng tháng phải >= 0")
    private BigDecimal monthlyIncome;

    private Boolean hasSocialInsurance;
    private String verificationDocType;
    private List<String> verificationDocUrls;

    private String idCardFrontUrl;
    private String idCardBackUrl;
    private String portraitUrl;
    private CustomerStatus status;
    private String notes;

    @JsonSetter("verificationDocType")
    public void setVerificationDocType(String verificationDocType) {
        this.verificationDocType = VerificationDocumentPolicy.normalizeDocCode(verificationDocType);
    }

    public void setVerificationDocType(VerificationDocumentType verificationDocType) {
        this.verificationDocType = verificationDocType != null ? verificationDocType.name() : null;
    }

    public static class CustomerUpdateRequestBuilder {
        public CustomerUpdateRequestBuilder verificationDocType(String verificationDocType) {
            this.verificationDocType = VerificationDocumentPolicy.normalizeDocCode(verificationDocType);
            return this;
        }

        public CustomerUpdateRequestBuilder verificationDocType(VerificationDocumentType verificationDocType) {
            this.verificationDocType = verificationDocType != null ? verificationDocType.name() : null;
            return this;
        }
    }
}

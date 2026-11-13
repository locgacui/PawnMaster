package com.pawnmaster.api.modules.customer.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.CustomerStatus;
import com.pawnmaster.api.modules.customer.VerificationDocumentPolicy;
import com.pawnmaster.api.modules.customer.VerificationDocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {

    private Long id;
    private String customerCode;
    private String fullName;
    private String identityNumber;
    private LocalDate identityIssueDate;
    private String identityIssuePlace;
    private String phoneNumber;
    private String email;
    private LocalDate dateOfBirth;
    private String gender;
    private String provinceCode;
    private String provinceName;
    private String districtCode;
    private String districtName;
    private String wardCode;
    private String wardName;
    private String addressDetail;
    private String address;
    private String permanentAddress;
    private String fullAddress;

    private CustomerOccupationGroup occupationGroup;
    private String occupationGroupDescription;
    private BigDecimal monthlyIncome;
    private boolean hasSocialInsurance;

    private String verificationDocType;
    private String verificationDocTypeDescription;
    private String verificationDocUrls;
    private List<String> verificationDocUrlList;

    private String idCardFrontUrl;
    private String idCardBackUrl;
    private String portraitUrl;
    private CustomerStatus status;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @JsonSetter("verificationDocType")
    public void setVerificationDocType(String verificationDocType) {
        this.verificationDocType = VerificationDocumentPolicy.normalizeDocCode(verificationDocType);
    }

    public void setVerificationDocType(VerificationDocumentType verificationDocType) {
        this.verificationDocType = verificationDocType != null ? verificationDocType.name() : null;
    }

    public static class CustomerResponseBuilder {
        public CustomerResponseBuilder verificationDocType(String verificationDocType) {
            this.verificationDocType = VerificationDocumentPolicy.normalizeDocCode(verificationDocType);
            return this;
        }

        public CustomerResponseBuilder verificationDocType(VerificationDocumentType verificationDocType) {
            this.verificationDocType = verificationDocType != null ? verificationDocType.name() : null;
            return this;
        }
    }
}

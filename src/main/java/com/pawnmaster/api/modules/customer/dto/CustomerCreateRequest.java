package com.pawnmaster.api.modules.customer.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.VerificationDocumentPolicy;
import com.pawnmaster.api.modules.customer.VerificationDocumentType;
import jakarta.validation.constraints.NotBlank;
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
public class CustomerCreateRequest {

    @Size(max = 50)
    private String customerCode;

    @NotBlank(message = "Họ và tên khách hàng không được để trống")
    @Size(max = 150)
    private String fullName;

    @NotBlank(message = "Số CCCD/CMND không được để trống")
    @Size(max = 25)
    private String identityNumber;

    private LocalDate identityIssueDate;
    private String identityIssuePlace;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Size(max = 20)
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

    @Builder.Default
    private CustomerOccupationGroup occupationGroup = CustomerOccupationGroup.FREELANCER;

    @PositiveOrZero(message = "Thu nhập hàng tháng phải >= 0")
    private BigDecimal monthlyIncome;

    @Builder.Default
    private boolean hasSocialInsurance = false;

    private String verificationDocType;
    private List<String> verificationDocUrls;

    private String idCardFrontUrl;
    private String idCardBackUrl;
    private String portraitUrl;
    private String notes;

    @JsonSetter("verificationDocType")
    public void setVerificationDocType(String verificationDocType) {
        this.verificationDocType = VerificationDocumentPolicy.normalizeDocCode(verificationDocType);
    }

    public void setVerificationDocType(VerificationDocumentType verificationDocType) {
        this.verificationDocType = verificationDocType != null ? verificationDocType.name() : null;
    }

    public static class CustomerCreateRequestBuilder {
        public CustomerCreateRequestBuilder verificationDocType(String verificationDocType) {
            this.verificationDocType = VerificationDocumentPolicy.normalizeDocCode(verificationDocType);
            return this;
        }

        public CustomerCreateRequestBuilder verificationDocType(VerificationDocumentType verificationDocType) {
            this.verificationDocType = verificationDocType != null ? verificationDocType.name() : null;
            return this;
        }
    }
}

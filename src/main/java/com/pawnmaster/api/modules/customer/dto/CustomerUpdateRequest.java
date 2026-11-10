package com.pawnmaster.api.modules.customer.dto;

import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.CustomerStatus;
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

    private Long branchId;
    private LocalDate identityIssueDate;
    private String identityIssuePlace;
    private String phoneNumber;
    private String email;
    private LocalDate dateOfBirth;
    private String gender;
    private String provinceCode;
    private String districtCode;
    private String address;
    private String permanentAddress;

    private CustomerOccupationGroup occupationGroup;

    @PositiveOrZero(message = "Thu nhập hàng tháng phải >= 0")
    private BigDecimal monthlyIncome;

    private Boolean hasSocialInsurance;
    private VerificationDocumentType verificationDocType;
    private List<String> verificationDocUrls;

    private String idCardFrontUrl;
    private String idCardBackUrl;
    private String portraitUrl;
    private CustomerStatus status;
    private String notes;
}

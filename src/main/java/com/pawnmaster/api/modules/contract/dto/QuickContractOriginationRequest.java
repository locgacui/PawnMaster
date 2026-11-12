package com.pawnmaster.api.modules.contract.dto;

import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.contract.InterestType;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.VerificationDocumentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request payload for creating a complete Pawn Contract from the Dashboard Quick-Action Modal
 * (Customer lookup/creation + Collateral registration + Dual-Layer LTV validation + Contract origination in PENDING_APPROVAL).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuickContractOriginationRequest {

    @NotNull(message = "Chi nhánh không được để trống")
    private Long branchId;

    // 1. Customer Information
    @NotBlank(message = "Họ tên khách hàng không được để trống")
    private String customerFullName;

    @NotBlank(message = "Số CCCD/Định danh không được để trống")
    private String identityNumber;

    @NotBlank(message = "Số điện thoại không được để trống")
    private String phoneNumber;

    private LocalDate identityIssueDate;

    private String identityIssuePlace;

    private LocalDate dateOfBirth;

    private String gender;

    private String email;

    private Boolean hasSocialInsurance;

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

    @NotNull(message = "Nhóm nghề nghiệp không được để trống")
    private CustomerOccupationGroup occupationGroup;

    private BigDecimal monthlyIncome;

    private VerificationDocumentType verificationDocType;

    private String verificationDocUrls;

    private String idCardFrontUrl;

    private String idCardBackUrl;

    private String portraitUrl;

    // 2. Collateral Information
    @NotNull(message = "Danh mục tài sản cầm cố không được để trống")
    private CollateralCategory collateralCategory;

    @NotBlank(message = "Tên tài sản cầm cố không được để trống")
    private String collateralName;

    private String brand;

    private String model;

    private String serialOrFrameNumber;

    private String licensePlate;

    private String storageLocation;

    private String conditionDescription;

    private String collateralImageUrls;

    @NotNull(message = "Giá trị thẩm định không được để trống")
    @DecimalMin(value = "100000", message = "Giá trị thẩm định phải từ 100.000 VNĐ trở lên")
    private BigDecimal appraisedValue;

    // 3. Loan Terms
    @NotNull(message = "Số tiền vay yêu cầu không được để trống")
    @DecimalMin(value = "100000", message = "Số tiền vay phải từ 100.000 VNĐ trở lên")
    private BigDecimal principalAmount;

    @Builder.Default
    private InterestType interestType = InterestType.DAILY_PER_MILLION;

    @NotNull(message = "Lãi suất không được để trống")
    private BigDecimal interestRate;

    @Builder.Default
    private BigDecimal storageFeeRate = BigDecimal.ZERO;

    @Builder.Default
    private Integer loanTermDays = 30;

    @Builder.Default
    private Integer paymentCycleDays = 10;

    private LocalDate startDate;

    private String notes;
}


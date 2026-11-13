package com.pawnmaster.api.modules.contract.dto;

import com.pawnmaster.api.modules.contract.DisbursementMethod;
import com.pawnmaster.api.modules.contract.InterestType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractCreateRequest {

    @NotNull(message = "Mã khách hàng không được để trống")
    private Long customerId;

    @NotNull(message = "Mã tài sản không được để trống")
    private Long collateralId;

    private Long branchId;

    @NotNull(message = "Số tiền vay không được để trống")
    @DecimalMin(value = "1000", message = "Số tiền vay tối thiểu là 1,000 VND")
    private BigDecimal principalAmount;

    @Builder.Default
    private InterestType interestType = InterestType.DAILY_PER_MILLION;

    @NotNull(message = "Lãi suất không được để trống")
    @DecimalMin(value = "0.0001", message = "Lãi suất phải lớn hơn 0")
    private BigDecimal interestRate;

    @Builder.Default
    private BigDecimal storageFeeRate = BigDecimal.ZERO;

    @Builder.Default
    private Integer loanTermDays = 30;

    @Builder.Default
    private Integer paymentCycleDays = 10;

    private LocalDate startDate;

    private String notes;

    @Builder.Default
    private DisbursementMethod disbursementMethod = DisbursementMethod.CASH;

    private String disbursementBankCode;

    private String disbursementBankName;

    private String disbursementAccountNumber;

    private String disbursementAccountHolder;
}

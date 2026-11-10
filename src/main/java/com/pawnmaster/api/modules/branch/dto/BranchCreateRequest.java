package com.pawnmaster.api.modules.branch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchCreateRequest {

    @NotBlank(message = "Mã chi nhánh không được để trống")
    @Size(max = 50, message = "Mã chi nhánh tối đa 50 ký tự")
    private String branchCode;

    @NotBlank(message = "Tên chi nhánh không được để trống")
    @Size(max = 150, message = "Tên chi nhánh tối đa 150 ký tự")
    private String branchName;

    @NotBlank(message = "Mã tỉnh/thành phố không được để trống")
    @Size(max = 20, message = "Mã tỉnh/thành phố tối đa 20 ký tự")
    private String provinceCode;

    @NotBlank(message = "Mã quận/huyện không được để trống")
    @Size(max = 20, message = "Mã quận/huyện tối đa 20 ký tự")
    private String districtCode;

    @Size(max = 20, message = "Mã phường/xã tối đa 20 ký tự")
    private String wardCode;

    @Size(max = 255, message = "Địa chỉ chi tiết tối đa 255 ký tự")
    private String addressDetail;

    @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
    private String phoneNumber;

    @PositiveOrZero(message = "Hạn mức vốn phải >= 0")
    @Builder.Default
    private BigDecimal capitalLimit = new BigDecimal("2500000000.00");

    @PositiveOrZero(message = "Số dư tiền mặt ban đầu phải >= 0")
    @Builder.Default
    private BigDecimal initialCashBalance = new BigDecimal("500000000.00");

    private BigDecimal currentCashBalance;

    public BigDecimal resolveInitialCash() {
        // nếu có số dư tiền mặt hiện tại => trả về số dư hiện tại
        if (currentCashBalance != null) {
            return currentCashBalance;
        }
        // nếu không có số dư tiền mặt ban đầu => lấy số dư tiền mặt ban đầu = 50tr
        return initialCashBalance != null ? initialCashBalance : new BigDecimal("500000000.00");
    }
}

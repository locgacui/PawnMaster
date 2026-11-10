package com.pawnmaster.api.modules.branch.dto;

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
public class BranchUpdateRequest {

    @Size(max = 150, message = "Tên chi nhánh tối đa 150 ký tự")
    private String branchName;

    @Size(max = 20, message = "Mã tỉnh/thành phố tối đa 20 ký tự")
    private String provinceCode;

    @Size(max = 20, message = "Mã quận/huyện tối đa 20 ký tự")
    private String districtCode;

    @Size(max = 20, message = "Mã phường/xã tối đa 20 ký tự")
    private String wardCode;

    @Size(max = 255, message = "Địa chỉ chi tiết tối đa 255 ký tự")
    private String addressDetail;

    @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
    private String phoneNumber;

    private BigDecimal capitalLimit;

    private BigDecimal currentCashBalance;

    private Boolean active;
}

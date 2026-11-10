package com.pawnmaster.api.modules.user.dto;

import com.pawnmaster.api.modules.user.RoleEnum;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    @Size(max = 150, message = "Họ và tên tối đa 150 ký tự")
    private String fullName;

    @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
    private String phoneNumber;

    @Email(message = "Email không hợp lệ")
    @Size(max = 120, message = "Email tối đa 120 ký tự")
    private String email;

    private RoleEnum role;

    private Long branchId;

    private Boolean active;

    @Size(min = 6, max = 100, message = "Mật khẩu mới từ 6 đến 100 ký tự")
    private String password;
}

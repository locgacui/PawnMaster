package com.pawnmaster.api.modules.user.dto;

import com.pawnmaster.api.modules.user.RoleEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;

    @Builder.Default
    private String tokenType = "Bearer";

    private long expiresIn;

    private Long userId;

    private String username;

    private String fullName;

    private RoleEnum role;

    private Long branchId;

    private String branchCode;

    private String branchName;

    private String provinceOrCity;

    private String provinceCode;

    private String districtCode;
}

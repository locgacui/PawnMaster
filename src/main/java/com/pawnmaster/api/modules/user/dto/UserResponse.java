package com.pawnmaster.api.modules.user.dto;

import com.pawnmaster.api.modules.user.RoleEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String username;
    private String fullName;
    private String phoneNumber;
    private String email;
    private RoleEnum role;
    private Long branchId;
    private String branchCode;
    private String branchName;
    private boolean active;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

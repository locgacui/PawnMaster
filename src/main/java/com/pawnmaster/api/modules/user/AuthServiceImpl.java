package com.pawnmaster.api.modules.user;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.user.dto.AuthResponse;
import com.pawnmaster.api.modules.user.dto.LoginRequest;
import com.pawnmaster.api.modules.user.dto.UserResponse;
import com.pawnmaster.api.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String username = request.getUsername() != null ? request.getUsername().trim() : "";
        String rawPassword = request.getPassword() != null ? request.getPassword() : "";

        UserEntity user = userRepository.findByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Tên đăng nhập hoặc mật khẩu không chính xác"));

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Tên đăng nhập hoặc mật khẩu không chính xác");
        }

        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.USER_INACTIVE, "Tài khoản người dùng đã bị khóa hoặc ngừng hoạt động");
        }

        if (user.getBranch() != null && !user.getBranch().isActive() && user.getRole() != RoleEnum.ADMIN) {
            throw new BusinessException(ErrorCode.BRANCH_INACTIVE, "Chi nhánh đang tạm ngưng hoạt động");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtTokenProvider.generateToken(user);

        Long branchId = null;
        String branchCode = null;
        String branchName = null;
        String provinceOrCity = null;
        String provinceCode = null;
        String districtCode = null;

        if (user.getBranch() != null) {
            branchId = user.getBranch().getId();
            branchCode = user.getBranch().getBranchCode();
            branchName = user.getBranch().getBranchName();
            provinceCode = user.getBranch().getProvinceCode();
            districtCode = user.getBranch().getDistrictCode();
            if (user.getBranch().getProvince() != null) {
                String fullName = user.getBranch().getProvince().getFullName();
                provinceOrCity = (fullName != null && !fullName.isBlank())
                        ? fullName
                        : user.getBranch().getProvince().getName();
            } else {
                provinceOrCity = user.getBranch().getProvinceCode();
            }
        }

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(86400000L)
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole())
                .branchId(branchId)
                .branchCode(branchCode)
                .branchName(branchName)
                .provinceOrCity(provinceOrCity)
                .provinceCode(provinceCode)
                .districtCode(districtCode)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String username) {
        UserEntity user = userRepository.findByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .email(user.getEmail())
                .role(user.getRole())
                .branchId(user.getBranchId())
                .branchCode(user.getBranchCode())
                .branchName(user.getBranchName())
                .active(user.isActive())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}

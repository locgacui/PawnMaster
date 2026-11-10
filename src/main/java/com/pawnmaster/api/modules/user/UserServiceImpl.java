package com.pawnmaster.api.modules.user;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.branch.BranchRepository;
import com.pawnmaster.api.modules.user.dto.UserCreateRequest;
import com.pawnmaster.api.modules.user.dto.UserResponse;
import com.pawnmaster.api.modules.user.dto.UserUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        String username = request.getUsername().trim();

        if (userRepository.existsByUsername(username) || userRepository.existsByUsernameAndDeletedFalse(username)) {
            throw new BusinessException(ErrorCode.CONFLICT_DATA, "Tên đăng nhập đã tồn tại: " + username);
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String email = request.getEmail().trim();
            if (userRepository.existsByEmail(email) || userRepository.existsByEmailAndDeletedFalse(email)) {
                throw new BusinessException(ErrorCode.EMAIL_EXISTS, "Email đã được sử dụng: " + email);
            }
        }

        BranchEntity branch = null;
        if (request.getBranchId() != null) {
            branch = branchRepository.findByIdAndDeletedFalse(request.getBranchId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh với ID: " + request.getBranchId()));
        } else if (request.getRole() != null && request.getRole() != RoleEnum.ADMIN) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Nhân viên phải được liên kết với một chi nhánh");
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());

        UserEntity user = UserEntity.builder()
                .username(username)
                .passwordHash(passwordHash)
                .fullName(request.getFullName().trim())
                .phoneNumber(request.getPhoneNumber())
                .email(request.getEmail() != null ? request.getEmail().trim() : null)
                .role(request.getRole() != null ? request.getRole() : RoleEnum.STAFF)
                .branch(branch)
                .active(true)
                .build();

        UserEntity saved = userRepository.save(user);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        UserEntity user = userRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng với ID: " + id));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber().trim());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            user.setEmail(request.getEmail().trim());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        if (request.getBranchId() != null) {
            BranchEntity branch = branchRepository.findByIdAndDeletedFalse(request.getBranchId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh với ID: " + request.getBranchId()));
            user.setBranch(branch);
        }
        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        UserEntity updated = userRepository.save(user);
        return toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        UserEntity user = userRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng với ID: " + id));
        return toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserByUsername(String username) {
        UserEntity user = userRepository.findByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng với username: " + username));
        return toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByBranch(Long branchId) {
        return userRepository.findByBranchIdAndDeletedFalse(branchId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAllByDeletedFalse()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        UserEntity user = userRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng với ID: " + id));
        user.setDeleted(true);
        user.setActive(false);
        userRepository.save(user);
    }

    private UserResponse toResponse(UserEntity user) {
        Long branchId = user.getBranchId();
        String branchCode = user.getBranchCode();
        String branchName = user.getBranchName();

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .email(user.getEmail())
                .role(user.getRole())
                .branchId(branchId)
                .branchCode(branchCode)
                .branchName(branchName)
                .active(user.isActive())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}

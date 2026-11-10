package com.pawnmaster.api.modules.user;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.branch.BranchRepository;
import com.pawnmaster.api.modules.user.dto.UserCreateRequest;
import com.pawnmaster.api.modules.user.dto.UserResponse;
import com.pawnmaster.api.modules.user.dto.UserUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private BranchEntity branch;
    private UserCreateRequest createRequest;
    private UserEntity sampleUser;

    @BeforeEach
    void setUp() {
        branch = BranchEntity.builder()
                .branchCode("CN-HCM-01")
                .branchName("Chi nhánh Quận 1")
                .provinceCode("79")
                .districtCode("760")
                .build();
        branch.setId(10L);

        createRequest = UserCreateRequest.builder()
                .username("staff_user01")
                .password("RawPass@123")
                .fullName("Trần Thị Nhân Viên")
                .phoneNumber("0901234567")
                .email("staff01@pawnmaster.vn")
                .role(RoleEnum.STAFF)
                .branchId(10L)
                .build();

        sampleUser = UserEntity.builder()
                .username("staff_user01")
                .passwordHash("$2a$10$hashedSecurePasswordString")
                .fullName("Trần Thị Nhân Viên")
                .phoneNumber("0901234567")
                .email("staff01@pawnmaster.vn")
                .role(RoleEnum.STAFF)
                .branch(branch)
                .active(true)
                .build();
        sampleUser.setId(100L);
    }

    @Test
    @DisplayName("createUser - Thành công với mã hóa mật khẩu BCrypt và liên kết chi nhánh")
    void createUser_Success_WithBranchBindingAndBCryptHashing() {
        when(userRepository.existsByUsername("staff_user01")).thenReturn(false);
        when(branchRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(branch));
        when(passwordEncoder.encode("RawPass@123")).thenReturn("$2a$10$hashedSecurePasswordString");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return entity;
        });

        UserResponse response = userService.createUser(createRequest);

        assertNotNull(response);
        assertEquals("staff_user01", response.getUsername());
        assertEquals("Trần Thị Nhân Viên", response.getFullName());
        assertEquals(RoleEnum.STAFF, response.getRole());
        assertEquals(10L, response.getBranchId());
        assertEquals("CN-HCM-01", response.getBranchCode());
        assertEquals("Chi nhánh Quận 1", response.getBranchName());

        verify(passwordEncoder, times(1)).encode("RawPass@123");
        verify(userRepository, times(1)).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("createUser - Ném CONFLICT_DATA khi username đã tồn tại")
    void createUser_DuplicateUsername_ThrowsConflictException() {
        when(userRepository.existsByUsername("staff_user01")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.createUser(createRequest));

        assertTrue(exception.getErrorCode() == ErrorCode.CONFLICT_DATA || exception.getErrorCode() == ErrorCode.USERNAME_EXISTS);
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("createUser - Ném BRANCH_NOT_FOUND khi chi nhánh liên kết không tồn tại")
    void createUser_BranchNotFound_ThrowsBranchNotFoundException() {
        when(userRepository.existsByUsername("staff_user01")).thenReturn(false);
        when(branchRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.createUser(createRequest));

        assertEquals(ErrorCode.BRANCH_NOT_FOUND, exception.getErrorCode());
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("createUser - Ném INVALID_REQUEST khi nhân viên không có chi nhánh")
    void createUser_StaffWithoutBranch_ThrowsException() {
        createRequest.setBranchId(null);
        createRequest.setRole(RoleEnum.STAFF);
        when(userRepository.existsByUsername("staff_user01")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.createUser(createRequest));

        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
    }

    @Test
    @DisplayName("createUser - Hỗ trợ tạo các vai trò MANAGER, ACCOUNTANT, ADMIN")
    void createUser_RolesSupport_Success() {
        for (RoleEnum role : RoleEnum.values()) {
            createRequest.setUsername("user_" + role.name().toLowerCase());
            createRequest.setRole(role);

            when(userRepository.existsByUsername(createRequest.getUsername())).thenReturn(false);
            if (role == RoleEnum.ADMIN) {
                createRequest.setBranchId(null);
            } else {
                createRequest.setBranchId(10L);
                when(branchRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(branch));
            }
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedPassword");
            when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
                UserEntity u = inv.getArgument(0);
                u.setId(200L);
                return u;
            });

            UserResponse res = userService.createUser(createRequest);
            assertEquals(role, res.getRole());
        }
    }

    @Test
    @DisplayName("getUserById - Thành công và thất bại khi không tìm thấy")
    void getUserById_Tests() {
        when(userRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(sampleUser));
        UserResponse response = userService.getUserById(100L);
        assertNotNull(response);
        assertEquals(100L, response.getId());

        when(userRepository.findByIdAndDeletedFalse(999L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class, () -> userService.getUserById(999L));
        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("updateUser - Cập nhật thông tin người dùng")
    void updateUser_Success() {
        UserUpdateRequest updateReq = UserUpdateRequest.builder()
                .fullName("Trần Thị Nhân Viên Mới")
                .phoneNumber("0988776655")
                .build();

        when(userRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse res = userService.updateUser(100L, updateReq);
        assertEquals("Trần Thị Nhân Viên Mới", res.getFullName());
        assertEquals("0988776655", res.getPhoneNumber());
    }

    @Test
    @DisplayName("deleteUser - Xóa mềm người dùng")
    void deleteUser_Success() {
        when(userRepository.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.deleteUser(100L);
        assertTrue(sampleUser.isDeleted());
        assertFalse(sampleUser.isActive());
    }
}

package com.pawnmaster.api.modules.user;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.branch.ProvinceEntity;
import com.pawnmaster.api.modules.user.dto.AuthResponse;
import com.pawnmaster.api.modules.user.dto.LoginRequest;
import com.pawnmaster.api.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthServiceImpl authService;

    private UserEntity managerUser;
    private BranchEntity branch;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        ProvinceEntity province = new ProvinceEntity();
        province.setCode("79");
        province.setName("Thành phố Hồ Chí Minh");

        branch = BranchEntity.builder()
                .branchCode("CN-HCM-01")
                .branchName("Chi nhánh Quận 1")
                .provinceCode("79")
                .districtCode("760")
                .province(province)
                .capitalLimit(BigDecimal.valueOf(2500000000L))
                .currentCashBalance(BigDecimal.valueOf(500000000L))
                .active(true)
                .build();
        branch.setId(1L);

        managerUser = UserEntity.builder()
                .username("manager_hcm01")
                .passwordHash("$2a$10$hashedManagerPassword")
                .fullName("Lê Quản Lý")
                .role(RoleEnum.MANAGER)
                .branch(branch)
                .active(true)
                .build();
        managerUser.setId(5L);

        loginRequest = LoginRequest.builder()
                .username("manager_hcm01")
                .password("CorrectPass@123")
                .build();
    }

    @Test
    @DisplayName("login - Đăng nhập thành công, trả về JWT mang ngữ cảnh chi nhánh")
    void login_Success_ReturnsJwtWithBranchContext() {
        when(userRepository.findByUsernameAndDeletedFalse("manager_hcm01"))
                .thenReturn(Optional.of(managerUser));
        when(passwordEncoder.matches("CorrectPass@123", "$2a$10$hashedManagerPassword"))
                .thenReturn(true);
        when(jwtTokenProvider.generateToken(any(UserEntity.class)))
                .thenReturn("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.dummy_token");

        AuthResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.dummy_token", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(5L, response.getUserId());
        assertEquals("manager_hcm01", response.getUsername());
        assertEquals(RoleEnum.MANAGER, response.getRole());
        assertEquals(1L, response.getBranchId());
        assertEquals("CN-HCM-01", response.getBranchCode());
        assertEquals("Chi nhánh Quận 1", response.getBranchName());
        assertEquals("Thành phố Hồ Chí Minh", response.getProvinceOrCity());

        verify(userRepository, times(1)).save(managerUser);
        assertNotNull(managerUser.getLastLoginAt());
    }

    @Test
    @DisplayName("login - Thất bại khi username không tồn tại trong hệ thống")
    void login_UserNotFound_ThrowsUnauthorized() {
        when(userRepository.findByUsernameAndDeletedFalse("manager_hcm01"))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest));

        assertTrue(ex.getErrorCode() == ErrorCode.UNAUTHORIZED || ex.getErrorCode() == ErrorCode.INVALID_CREDENTIALS);
        verify(jwtTokenProvider, never()).generateToken(any(UserEntity.class));
    }

    @Test
    @DisplayName("login - Thất bại khi mật khẩu không khớp")
    void login_WrongPassword_ThrowsUnauthorized() {
        when(userRepository.findByUsernameAndDeletedFalse("manager_hcm01"))
                .thenReturn(Optional.of(managerUser));
        when(passwordEncoder.matches("CorrectPass@123", "$2a$10$hashedManagerPassword"))
                .thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest));

        assertTrue(ex.getErrorCode() == ErrorCode.UNAUTHORIZED || ex.getErrorCode() == ErrorCode.INVALID_CREDENTIALS);
        verify(jwtTokenProvider, never()).generateToken(any(UserEntity.class));
    }

    @Test
    @DisplayName("login - Thất bại khi tài khoản bị khóa active=false")
    void login_InactiveUser_ThrowsUnauthorized() {
        managerUser.setActive(false);
        when(userRepository.findByUsernameAndDeletedFalse("manager_hcm01"))
                .thenReturn(Optional.of(managerUser));
        when(passwordEncoder.matches("CorrectPass@123", "$2a$10$hashedManagerPassword"))
                .thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest));

        assertTrue(ex.getErrorCode() == ErrorCode.UNAUTHORIZED || ex.getErrorCode() == ErrorCode.FORBIDDEN || ex.getErrorCode() == ErrorCode.USER_INACTIVE);
        verify(jwtTokenProvider, never()).generateToken(any(UserEntity.class));
    }

    @Test
    @DisplayName("login - Thất bại khi chi nhánh bị ngưng hoạt động")
    void login_InactiveBranch_ThrowsBranchInactive() {
        branch.setActive(false);
        when(userRepository.findByUsernameAndDeletedFalse("manager_hcm01"))
                .thenReturn(Optional.of(managerUser));
        when(passwordEncoder.matches("CorrectPass@123", "$2a$10$hashedManagerPassword"))
                .thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest));

        assertEquals(ErrorCode.BRANCH_INACTIVE, ex.getErrorCode());
        verify(jwtTokenProvider, never()).generateToken(any(UserEntity.class));
    }

    @Test
    @DisplayName("login - Admin không gắn chi nhánh đăng nhập thành công")
    void login_AdminWithoutBranch_Success() {
        UserEntity adminUser = UserEntity.builder()
                .username("admin_root")
                .passwordHash("$2a$10$hashedAdminPassword")
                .fullName("Quản Trị Viên")
                .role(RoleEnum.ADMIN)
                .branch(null)
                .active(true)
                .build();
        adminUser.setId(1L);

        LoginRequest adminLogin = LoginRequest.builder()
                .username("admin_root")
                .password("AdminPass@123")
                .build();

        when(userRepository.findByUsernameAndDeletedFalse("admin_root"))
                .thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("AdminPass@123", "$2a$10$hashedAdminPassword"))
                .thenReturn(true);
        when(jwtTokenProvider.generateToken(any(UserEntity.class)))
                .thenReturn("admin_jwt_token");

        AuthResponse res = authService.login(adminLogin);

        assertNotNull(res);
        assertEquals(RoleEnum.ADMIN, res.getRole());
        assertNull(res.getBranchId());
        assertNull(res.getBranchCode());
    }
}

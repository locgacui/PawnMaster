package com.pawnmaster.api.modules.branch;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.branch.dto.BranchCreateRequest;
import com.pawnmaster.api.modules.branch.dto.BranchResponse;
import com.pawnmaster.api.modules.user.*;
import com.pawnmaster.api.modules.user.dto.AuthResponse;
import com.pawnmaster.api.modules.user.dto.LoginRequest;
import com.pawnmaster.api.modules.user.dto.UserCreateRequest;
import com.pawnmaster.api.modules.user.dto.UserResponse;
import com.pawnmaster.api.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Milestone 1 Empirical Boundary & Adversarial Challenge Suite")
public class Milestone1BoundaryEmpiricalTest {

        // Branch Service Mocks
        @Mock
        private BranchRepository branchRepository;
        @Mock
        private ProvinceRepository provinceRepository;
        @Mock
        private DistrictRepository districtRepository;
        @Mock
        private WardRepository wardRepository;
        @InjectMocks
        private BranchServiceImpl branchService;

        // User Service Mocks
        @Mock
        private UserRepository userRepository;
        @Mock
        private PasswordEncoder passwordEncoder;
        @InjectMocks
        private UserServiceImpl userService;

        // Auth Service
        private AuthServiceImpl authService;
        private JwtTokenProvider realJwtTokenProvider;

        private BranchEntity activeBranch;
        private BranchEntity inactiveBranch;
        private UserEntity activeStaffUser;
        private UserEntity inactiveUser;
        private UserEntity adminUser;

        @BeforeEach
        void setUp() {
                // Initialize real JwtTokenProvider
                realJwtTokenProvider = new JwtTokenProvider();
                ReflectionTestUtils.setField(realJwtTokenProvider, "jwtSecret",
                                "cGF3bm1hc3Rlci1zZWNyZXQta2V5LWZvci1qd3QtYXV0aGVudGljYXRpb24tMjAyNi1zZWN1cmUta2V5");
                ReflectionTestUtils.setField(realJwtTokenProvider, "jwtExpirationMs", 86400000L);

                // Inject into AuthServiceImpl
                authService = new AuthServiceImpl(userRepository, passwordEncoder, realJwtTokenProvider);

                ProvinceEntity hcmProvince = ProvinceEntity.builder()
                                .code("79")
                                .name("Thành phố Hồ Chí Minh")
                                .fullName("Thành phố Hồ Chí Minh")
                                .build();

                DistrictEntity district1 = DistrictEntity.builder()
                                .code("760")
                                .name("Quận 1")
                                .provinceCode("79")
                                .province(hcmProvince)
                                .build();

                activeBranch = BranchEntity.builder()
                                .branchCode("CN-Q1")
                                .branchName("Chi nhánh Quận 1")
                                .provinceCode("79")
                                .province(hcmProvince)
                                .districtCode("760")
                                .district(district1)
                                .capitalLimit(new BigDecimal("5000000000.00"))
                                .currentCashBalance(new BigDecimal("1000000000.00"))
                                .active(true)
                                .build();
                activeBranch.setId(10L);

                inactiveBranch = BranchEntity.builder()
                                .branchCode("CN-DONG-CUA")
                                .branchName("Chi nhánh Tạm Dừng")
                                .provinceCode("79")
                                .province(hcmProvince)
                                .districtCode("760")
                                .district(district1)
                                .capitalLimit(new BigDecimal("1000000000.00"))
                                .currentCashBalance(BigDecimal.ZERO)
                                .active(false)
                                .build();
                inactiveBranch.setId(11L);

                activeStaffUser = UserEntity.builder()
                                .username("staff_empirical")
                                .passwordHash("$2a$10$hashedEmpiricalPassword")
                                .fullName("Nguyễn Văn Nhân Viên")
                                .role(RoleEnum.STAFF)
                                .branch(activeBranch)
                                .active(true)
                                .build();
                activeStaffUser.setId(201L);

                inactiveUser = UserEntity.builder()
                                .username("inactive_user")
                                .passwordHash("$2a$10$hashedEmpiricalPassword")
                                .fullName("Người Dùng Bị Khóa")
                                .role(RoleEnum.STAFF)
                                .branch(activeBranch)
                                .active(false)
                                .build();
                inactiveUser.setId(202L);

                adminUser = UserEntity.builder()
                                .username("admin_root")
                                .passwordHash("$2a$10$hashedAdminPassword")
                                .fullName("Tổng Quản Trị")
                                .role(RoleEnum.ADMIN)
                                .branch(null)
                                .active(true)
                                .build();
                adminUser.setId(1L);
        }

        // -------------------------------------------------------------------------
        // Objective 1.1: Creating user with role STAFF but branchId = null ->
        // INVALID_REQUEST
        // -------------------------------------------------------------------------
        @Test
        @DisplayName("Challenge 1: Tạo người dùng vai trò STAFF nhưng branchId = null -> phải bị từ chối với INVALID_REQUEST")
        void testCreateUser_StaffRoleWithNullBranch_ThrowsInvalidRequest() {
                UserCreateRequest req = UserCreateRequest.builder()
                                .username("staff_nobranch")
                                .password("Password@123")
                                .fullName("Nhân viên không chi nhánh")
                                .role(RoleEnum.STAFF)
                                .branchId(null)
                                .build();

                when(userRepository.existsByUsername(req.getUsername())).thenReturn(false);

                BusinessException ex = assertThrows(BusinessException.class, () -> userService.createUser(req));
                assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode(),
                                "Tạo người dùng STAFF với branchId = null bắt buộc phải ném mã lỗi INVALID_REQUEST");
                verify(userRepository, never()).save(any(UserEntity.class));
        }

        @Test
        @DisplayName("Challenge 1b: Tạo người dùng vai trò MANAGER/ACCOUNTANT nhưng branchId = null -> cũng phải ném INVALID_REQUEST")
        void testCreateUser_ManagerAndAccountantWithNullBranch_ThrowsInvalidRequest() {
                for (RoleEnum role : new RoleEnum[] { RoleEnum.MANAGER, RoleEnum.ACCOUNTANT }) {
                        UserCreateRequest req = UserCreateRequest.builder()
                                        .username("user_" + role.name().toLowerCase())
                                        .password("Password@123")
                                        .fullName("Người dùng " + role.name())
                                        .role(role)
                                        .branchId(null)
                                        .build();

                        when(userRepository.existsByUsername(req.getUsername())).thenReturn(false);

                        BusinessException ex = assertThrows(BusinessException.class, () -> userService.createUser(req));
                        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode(),
                                        "Vai trò " + role
                                                        + " bắt buộc phải liên kết chi nhánh, phải ném INVALID_REQUEST khi branchId null");
                }
        }

        @Test
        @DisplayName("Challenge 1c: Tạo người dùng vai trò ADMIN với branchId = null -> phải thành công")
        void testCreateUser_AdminWithNullBranch_Success() {
                UserCreateRequest req = UserCreateRequest.builder()
                                .username("admin_new")
                                .password("Admin@123456")
                                .fullName("Quản trị viên mới")
                                .role(RoleEnum.ADMIN)
                                .branchId(null)
                                .build();

                when(userRepository.existsByUsername("admin_new")).thenReturn(false);
                when(passwordEncoder.encode("Admin@123456")).thenReturn("$2a$10$encodedAdmin");
                when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
                        UserEntity u = inv.getArgument(0);
                        u.setId(99L);
                        return u;
                });

                UserResponse res = userService.createUser(req);
                assertNotNull(res);
                assertEquals(RoleEnum.ADMIN, res.getRole());
                assertNull(res.getBranchId());
        }

        // -------------------------------------------------------------------------
        // Objective 1.2: Creating a branch with duplicate branchCode ->
        // BRANCH_CODE_EXISTS
        // -------------------------------------------------------------------------
        @Test
        @DisplayName("Challenge 2: Tạo chi nhánh với branchCode đã tồn tại -> phải ném BRANCH_CODE_EXISTS")
        void testCreateBranch_DuplicateBranchCode_ThrowsBranchCodeExists() {
                BranchCreateRequest req = BranchCreateRequest.builder()
                                .branchCode("CN-Q1")
                                .branchName("Chi nhánh trùng mã")
                                .provinceCode("79")
                                .districtCode("760")
                                .build();

                when(branchRepository.existsByBranchCode("CN-Q1")).thenReturn(true);

                BusinessException ex = assertThrows(BusinessException.class, () -> branchService.createBranch(req));
                assertEquals(ErrorCode.BRANCH_CODE_EXISTS, ex.getErrorCode(),
                                "Trùng mã chi nhánh bắt buộc phải ném BRANCH_CODE_EXISTS");
                verify(branchRepository, never()).save(any(BranchEntity.class));
        }

        @Test
        @DisplayName("Challenge 2b: Tạo chi nhánh với branchCode có khoảng trắng thừa -> tự động trim và phát hiện trùng")
        void testCreateBranch_DuplicateBranchCodeWithWhitespace_ThrowsBranchCodeExists() {
                BranchCreateRequest req = BranchCreateRequest.builder()
                                .branchCode("  CN-Q1  ")
                                .branchName("Chi nhánh trùng mã khoảng trắng")
                                .provinceCode("79")
                                .districtCode("760")
                                .build();

                when(branchRepository.existsByBranchCode("CN-Q1")).thenReturn(true);

                BusinessException ex = assertThrows(BusinessException.class, () -> branchService.createBranch(req));
                assertEquals(ErrorCode.BRANCH_CODE_EXISTS, ex.getErrorCode());
        }

        // -------------------------------------------------------------------------
        // Objective 1.3: Creating a branch with non-existent provinceCode ->
        // PROVINCE_NOT_FOUND
        // -------------------------------------------------------------------------
        @Test
        @DisplayName("Challenge 3: Tạo chi nhánh với provinceCode không tồn tại -> phải ném PROVINCE_NOT_FOUND")
        void testCreateBranch_NonExistentProvinceCode_ThrowsProvinceNotFound() {
                BranchCreateRequest req = BranchCreateRequest.builder()
                                .branchCode("CN-UNKNOWN")
                                .branchName("Chi nhánh Tỉnh Lạ")
                                .provinceCode("9999")
                                .districtCode("760")
                                .build();

                when(branchRepository.existsByBranchCode("CN-UNKNOWN")).thenReturn(false);
                when(provinceRepository.findById("9999")).thenReturn(Optional.empty());
                when(provinceRepository.count()).thenReturn(63L);

                BusinessException ex = assertThrows(BusinessException.class, () -> branchService.createBranch(req));
                assertEquals(ErrorCode.PROVINCE_NOT_FOUND, ex.getErrorCode(),
                                "Province code không tồn tại bắt buộc phải ném PROVINCE_NOT_FOUND");
                verify(branchRepository, never()).save(any(BranchEntity.class));
        }

        @Test
        @DisplayName("Challenge 3b: Tạo chi nhánh với districtCode không tồn tại -> phải ném DISTRICT_NOT_FOUND")
        void testCreateBranch_NonExistentDistrictCode_ThrowsDistrictNotFound() {
                BranchCreateRequest req = BranchCreateRequest.builder()
                                .branchCode("CN-DIST-UNKNOWN")
                                .branchName("Chi nhánh Huyện Lạ")
                                .provinceCode("79")
                                .districtCode("8888")
                                .build();

                ProvinceEntity province = ProvinceEntity.builder().code("79").name("HCM").build();
                when(branchRepository.existsByBranchCode("CN-DIST-UNKNOWN")).thenReturn(false);
                when(provinceRepository.findById("79")).thenReturn(Optional.of(province));
                when(districtRepository.findById("8888")).thenReturn(Optional.empty());
                when(districtRepository.count()).thenReturn(100L);

                BusinessException ex = assertThrows(BusinessException.class, () -> branchService.createBranch(req));
                assertEquals(ErrorCode.DISTRICT_NOT_FOUND, ex.getErrorCode(),
                                "District code không tồn tại bắt buộc phải ném DISTRICT_NOT_FOUND");
        }

        // -------------------------------------------------------------------------
        // Objective 1.4: Authenticating an inactive user -> must throw USER_INACTIVE
        // -------------------------------------------------------------------------
        @Test
        @DisplayName("Challenge 4: Xác thực người dùng bị khóa (active = false) -> phải ném USER_INACTIVE")
        void testLogin_InactiveUser_ThrowsUserInactive() {
                LoginRequest req = LoginRequest.builder()
                                .username("inactive_user")
                                .password("Password@123")
                                .build();

                when(userRepository.findByUsernameAndDeletedFalse("inactive_user"))
                                .thenReturn(Optional.of(inactiveUser));
                when(passwordEncoder.matches("Password@123", "$2a$10$hashedEmpiricalPassword"))
                                .thenReturn(true);

                BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req));
                assertEquals(ErrorCode.USER_INACTIVE, ex.getErrorCode(),
                                "Đăng nhập người dùng inactive bắt buộc phải ném mã lỗi USER_INACTIVE");
        }

        // -------------------------------------------------------------------------
        // Objective 1.5: Authenticating with wrong password -> must throw
        // INVALID_CREDENTIALS
        // -------------------------------------------------------------------------
        @Test
        @DisplayName("Challenge 5: Xác thực sai mật khẩu -> phải ném INVALID_CREDENTIALS")
        void testLogin_WrongPassword_ThrowsInvalidCredentials() {
                LoginRequest req = LoginRequest.builder()
                                .username("staff_empirical")
                                .password("WrongPassword@999")
                                .build();

                when(userRepository.findByUsernameAndDeletedFalse("staff_empirical"))
                                .thenReturn(Optional.of(activeStaffUser));
                when(passwordEncoder.matches("WrongPassword@999", "$2a$10$hashedEmpiricalPassword"))
                                .thenReturn(false);

                BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req));
                assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode(),
                                "Sai mật khẩu bắt buộc phải ném mã lỗi INVALID_CREDENTIALS");
        }

        @Test
        @DisplayName("Challenge 5b: Xác thực tài khoản không tồn tại -> cũng phải ném INVALID_CREDENTIALS")
        void testLogin_NonExistentUser_ThrowsInvalidCredentials() {
                LoginRequest req = LoginRequest.builder()
                                .username("ghost_user")
                                .password("Password@123")
                                .build();

                when(userRepository.findByUsernameAndDeletedFalse("ghost_user"))
                                .thenReturn(Optional.empty());

                BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req));
                assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode(),
                                "Tài khoản không tồn tại bắt buộc phải ném INVALID_CREDENTIALS");
        }

        // -------------------------------------------------------------------------
        // Objective 1.6: Branch Inactivity & Admin Exception
        // -------------------------------------------------------------------------
        @Test
        @DisplayName("Challenge 6: Người dùng thuộc chi nhánh ngừng hoạt động -> ném BRANCH_INACTIVE")
        void testLogin_UserInInactiveBranch_ThrowsBranchInactive() {
                UserEntity userInInactiveBranch = UserEntity.builder()
                                .username("staff_closed_branch")
                                .passwordHash("$2a$10$pass")
                                .fullName("Nhân Viên Chi Nhánh Đóng")
                                .role(RoleEnum.STAFF)
                                .branch(inactiveBranch)
                                .active(true)
                                .build();

                LoginRequest req = LoginRequest.builder()
                                .username("staff_closed_branch")
                                .password("CorrectPass")
                                .build();

                when(userRepository.findByUsernameAndDeletedFalse("staff_closed_branch"))
                                .thenReturn(Optional.of(userInInactiveBranch));
                when(passwordEncoder.matches("CorrectPass", "$2a$10$pass"))
                                .thenReturn(true);

                BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req));
                assertEquals(ErrorCode.BRANCH_INACTIVE, ex.getErrorCode(),
                                "Chi nhánh ngưng hoạt động bắt buộc phải ném BRANCH_INACTIVE đối với non-admin");
        }

        @Test
        @DisplayName("Challenge 6b: ADMIN thuộc chi nhánh ngừng hoạt động -> vẫn được phép đăng nhập")
        void testLogin_AdminInInactiveBranch_Permitted() {
                UserEntity adminInInactiveBranch = UserEntity.builder()
                                .username("admin_in_inactive_branch")
                                .passwordHash("$2a$10$pass")
                                .fullName("Quản trị viên")
                                .role(RoleEnum.ADMIN)
                                .branch(inactiveBranch)
                                .active(true)
                                .build();
                adminInInactiveBranch.setId(77L);

                LoginRequest req = LoginRequest.builder()
                                .username("admin_in_inactive_branch")
                                .password("CorrectPass")
                                .build();

                when(userRepository.findByUsernameAndDeletedFalse("admin_in_inactive_branch"))
                                .thenReturn(Optional.of(adminInInactiveBranch));
                when(passwordEncoder.matches("CorrectPass", "$2a$10$pass"))
                                .thenReturn(true);

                AuthResponse res = authService.login(req);
                assertNotNull(res);
                assertNotNull(res.getAccessToken());
                assertEquals(RoleEnum.ADMIN, res.getRole());
        }

        // -------------------------------------------------------------------------
        // Objective 1.7: Empirical Token Verification with Real JwtTokenProvider
        // -------------------------------------------------------------------------
        @Test
        @DisplayName("Challenge 7: Kiểm tra token JWT phát hành thật sự mang đầy đủ Claims ngữ cảnh chi nhánh")
        void testJwtTokenProvider_RealToken_ContainsAllBranchClaims() {
                String token = realJwtTokenProvider.generateToken(activeStaffUser);
                assertNotNull(token);
                assertTrue(realJwtTokenProvider.validateToken(token));

                Claims claims = realJwtTokenProvider.getClaimsFromToken(token);
                assertEquals("staff_empirical", claims.getSubject());
                assertEquals(201, claims.get("userId", Integer.class));
                assertEquals("STAFF", claims.get("role", String.class));
                assertEquals("Nguyễn Văn Nhân Viên", claims.get("fullName", String.class));
                assertEquals(10, claims.get("branchId", Integer.class));
                assertEquals("CN-Q1", claims.get("branchCode", String.class));
                assertEquals("Chi nhánh Quận 1", claims.get("branchName", String.class));
                assertEquals("79", claims.get("provinceCode", String.class));
                assertEquals("760", claims.get("districtCode", String.class));
                assertEquals("Thành phố Hồ Chí Minh", claims.get("provinceOrCity", String.class));
        }

        @Test
        @DisplayName("Challenge 7b: Kiểm tra token JWT cho ADMIN không có chi nhánh -> không phát sinh NPE và bỏ qua claims chi nhánh")
        void testJwtTokenProvider_AdminWithoutBranch_GeneratesSafeToken() {
                String token = realJwtTokenProvider.generateToken(adminUser);
                assertNotNull(token);
                assertTrue(realJwtTokenProvider.validateToken(token));

                Claims claims = realJwtTokenProvider.getClaimsFromToken(token);
                assertEquals("admin_root", claims.getSubject());
                assertEquals(1, claims.get("userId", Integer.class));
                assertEquals("ADMIN", claims.get("role", String.class));
                assertNull(claims.get("branchId"));
                assertNull(claims.get("branchCode"));
        }
}

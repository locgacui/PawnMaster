package com.pawnmaster.api.modules.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawnmaster.api.modules.branch.BranchService;
import com.pawnmaster.api.modules.branch.dto.BranchResponse;
import com.pawnmaster.api.modules.cashbook.CashbookService;
import com.pawnmaster.api.modules.collateral.CollateralService;
import com.pawnmaster.api.modules.collateral.LtvMatrixPolicy;
import com.pawnmaster.api.modules.contract.ContractService;
import com.pawnmaster.api.modules.customer.CustomerService;
import com.pawnmaster.api.modules.dashboard.DashboardController;
import com.pawnmaster.api.modules.user.dto.AuthResponse;
import com.pawnmaster.api.modules.user.dto.LoginRequest;
import com.pawnmaster.api.security.JwtAuthenticationFilter;
import com.pawnmaster.api.security.JwtTokenProvider;
import com.pawnmaster.api.security.UserDetailsServiceImpl;
import com.pawnmaster.api.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Login Page, Cookie Session & Dashboard Auth Gate Suite")
class LoginAndAuthGateTest {

        @Mock
        private AuthService authService;

        @Mock
        private JwtTokenProvider jwtTokenProvider;

        @Mock
        private UserDetailsServiceImpl userDetailsService;

        @Mock
        private BranchService branchService;

        @Mock
        private ContractService contractService;

        @Mock
        private CashbookService cashbookService;

        @Mock
        private CustomerService customerService;

        @Mock
        private CollateralService collateralService;

        @Mock
        private LtvMatrixPolicy ltvMatrixPolicy;

        @InjectMocks
        private AuthController authController;

        @InjectMocks
        private DashboardController dashboardController;

        private MockMvc authMockMvc;
        private ObjectMapper objectMapper;

        @BeforeEach
        void setUp() {
                objectMapper = new ObjectMapper();
                authMockMvc = MockMvcBuilders.standaloneSetup(authController).build();
                SecurityContextHolder.clearContext();
        }

        @AfterEach
        void tearDown() {
                SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("POST /api/v1/auth/login should return JWT token and set HttpOnly pm_token cookie")
        void login_ShouldSetHttpOnlyCookieAndReturnAuthResponse() throws Exception {
                AuthResponse mockAuth = AuthResponse.builder()
                                .accessToken("jwt-cookie-test-token")
                                .tokenType("Bearer")
                                .expiresIn(86400000L)
                                .userId(2L)
                                .username("manager_q1")
                                .fullName("Nguyễn Văn Quản Lý")
                                .role(RoleEnum.MANAGER)
                                .branchId(1L)
                                .branchCode("CN-HCM-01")
                                .branchName("Chi nhánh Quận 1")
                                .build();

                when(authService.login(any(LoginRequest.class))).thenReturn(mockAuth);

                LoginRequest req = LoginRequest.builder()
                                .username("manager_q1")
                                .password("Manager@123")
                                .build();

                authMockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isOk())
                                .andExpect(cookie().exists(JwtAuthenticationFilter.JWT_COOKIE_NAME))
                                .andExpect(cookie().value(JwtAuthenticationFilter.JWT_COOKIE_NAME,
                                                "jwt-cookie-test-token"))
                                .andExpect(cookie().httpOnly(JwtAuthenticationFilter.JWT_COOKIE_NAME, true))
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.username").value("manager_q1"))
                                .andExpect(jsonPath("$.data.role").value("MANAGER"))
                                .andExpect(jsonPath("$.data.branchId").value(1));
        }

        @Test
        @DisplayName("POST /api/v1/auth/logout should expire pm_token cookie and clear security context")
        void logout_ShouldClearCookieAndContext() throws Exception {
                authMockMvc.perform(post("/api/v1/auth/logout"))
                                .andExpect(status().isOk())
                                .andExpect(cookie().exists(JwtAuthenticationFilter.JWT_COOKIE_NAME))
                                .andExpect(cookie().maxAge(JwtAuthenticationFilter.JWT_COOKIE_NAME, 0))
                                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("JwtAuthenticationFilter should authenticate request using pm_token Cookie when Authorization header is absent")
        void jwtFilter_ShouldAuthenticateFromCookie() throws Exception {
                JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);

                UserPrincipal principal = UserPrincipal.builder()
                                .id(2L)
                                .username("manager_q1")
                                .fullName("Nguyễn Văn Quản Lý")
                                .role(RoleEnum.MANAGER)
                                .branchId(1L)
                                .active(true)
                                .authorities(List.of(new SimpleGrantedAuthority("ROLE_MANAGER")))
                                .build();

                when(jwtTokenProvider.validateToken("valid-cookie-jwt")).thenReturn(true);
                when(jwtTokenProvider.getUsernameFromToken("valid-cookie-jwt")).thenReturn("manager_q1");
                when(userDetailsService.loadUserByUsername("manager_q1")).thenReturn(principal);

                MockHttpServletRequest request = new MockHttpServletRequest();
                request.setCookies(new Cookie(JwtAuthenticationFilter.JWT_COOKIE_NAME, "valid-cookie-jwt"));
                MockHttpServletResponse response = new MockHttpServletResponse();
                FilterChain filterChain = mock(FilterChain.class);

                filter.doFilter(request, response, filterChain);

                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
                assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("manager_q1");
                verify(filterChain, times(1)).doFilter(request, response);
        }

        @Test
        @DisplayName("GET /login when already authenticated should redirect to user's branch dashboard")
        void loginPage_WhenAuthenticated_ShouldRedirectToDashboardWithBranchId() {
                UserPrincipal principal = UserPrincipal.builder()
                                .id(2L)
                                .username("manager_q1")
                                .fullName("Nguyễn Văn Quản Lý")
                                .role(RoleEnum.MANAGER)
                                .branchId(2L)
                                .active(true)
                                .authorities(List.of(new SimpleGrantedAuthority("ROLE_MANAGER")))
                                .build();

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);

                String view = dashboardController.loginPage();

                assertThat(view).isEqualTo("redirect:/dashboard?branchId=2");
        }

        @Test
        @DisplayName("GET /dashboard when authenticated without branchId query param should default to user's assigned branch")
        void dashboard_WhenAuthenticated_ShouldDefaultToAssignedBranchAndPopulateCurrentUser() {
                BranchResponse b1 = BranchResponse.builder()
                                .id(1L)
                                .branchCode("CN01")
                                .branchName("Chi nhánh Quận 1")
                                .currentCashBalance(new BigDecimal("520000000"))
                                .active(true)
                                .build();
                BranchResponse b2 = BranchResponse.builder()
                                .id(2L)
                                .branchCode("CN02")
                                .branchName("Chi nhánh Bình Thạnh")
                                .currentCashBalance(new BigDecimal("350000000"))
                                .active(true)
                                .build();

                when(branchService.getActiveBranches()).thenReturn(List.of(b1, b2));
                when(contractService.getContractsByBranch(2L)).thenReturn(Collections.emptyList());

                UserPrincipal principal = UserPrincipal.builder()
                                .id(5L)
                                .username("staff_bt")
                                .fullName("Trần Thị Giao Dịch Viên")
                                .role(RoleEnum.STAFF)
                                .branchId(2L)
                                .active(true)
                                .authorities(List.of(new SimpleGrantedAuthority("ROLE_STAFF")))
                                .build();

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);

                Model model = new ConcurrentModel();
                String view = dashboardController.dashboard(null, model);

                assertThat(view).isEqualTo("dashboard");
                assertThat(model.getAttribute("currentUser")).isEqualTo(principal);
                assertThat(model.getAttribute("selectedBranch")).isEqualTo(b2);
                verify(contractService).getContractsByBranch(2L);
        }
}

package com.pawnmaster.api.modules.dashboard;

import com.pawnmaster.api.common.GlobalExceptionHandler;
import com.pawnmaster.api.config.SecurityConfig;
import com.pawnmaster.api.modules.branch.BranchController;
import com.pawnmaster.api.modules.branch.BranchService;
import com.pawnmaster.api.modules.branch.dto.BranchResponse;
import com.pawnmaster.api.modules.branch.dto.DistrictResponse;
import com.pawnmaster.api.modules.branch.dto.ProvinceResponse;
import com.pawnmaster.api.modules.branch.dto.WardResponse;
import com.pawnmaster.api.modules.cashbook.CashbookService;
import com.pawnmaster.api.modules.cashbook.dto.CashFlowSummaryResponse;
import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.contract.InterestType;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.VerificationDocumentType;
import com.pawnmaster.api.modules.user.RoleEnum;
import com.pawnmaster.api.security.BranchSecurityGuard;
import com.pawnmaster.api.security.JwtAuthenticationFilter;
import com.pawnmaster.api.security.JwtTokenProvider;
import com.pawnmaster.api.security.UserDetailsServiceImpl;
import com.pawnmaster.api.security.UserPrincipal;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@DisplayName("Dedicated Contract Origination Workspace (/contracts/new) — SSR, RBAC, Auth Gate & Cascading Address Suite")
class ContractOriginationViewControllerTest {

    private static BranchResponse buildBranch1() {
        return BranchResponse.builder()
                .id(1L)
                .branchCode("CN-HCM-01")
                .branchName("Chi nhánh Quận 1")
                .provinceCode("79")
                .provinceName("Thành phố Hồ Chí Minh")
                .districtCode("760")
                .districtName("Quận 1")
                .capitalLimit(new BigDecimal("2500000000"))
                .currentCashBalance(new BigDecimal("520000000"))
                .active(true)
                .build();
    }

    private static BranchResponse buildBranch2() {
        return BranchResponse.builder()
                .id(2L)
                .branchCode("CN-HCM-02")
                .branchName("Chi nhánh Quận Bình Thạnh")
                .provinceCode("79")
                .provinceName("Thành phố Hồ Chí Minh")
                .districtCode("765")
                .districtName("Quận Bình Thạnh")
                .capitalLimit(new BigDecimal("2000000000"))
                .currentCashBalance(new BigDecimal("350000000"))
                .active(true)
                .build();
    }

    private static List<ProvinceResponse> buildSampleProvinces() {
        return List.of(
                ProvinceResponse.builder()
                        .code("79")
                        .name("Hồ Chí Minh")
                        .fullName("Thành phố Hồ Chí Minh")
                        .codeName("ho_chi_minh")
                        .build(),
                ProvinceResponse.builder()
                        .code("01")
                        .name("Hà Nội")
                        .fullName("Thành phố Hà Nội")
                        .codeName("ha_noi")
                        .build()
        );
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("1. Controller SSR Model & RBAC Branch Isolation Unit Tests")
    class ControllerUnitAndRbacTests {

        @Mock
        private BranchService branchService;

        @Mock
        private CashbookService cashbookService;

        private ContractOriginationViewController controller;
        private MockMvc standaloneMockMvc;
        private BranchResponse branch1;
        private BranchResponse branch2;

        @BeforeEach
        void setUp() {
            InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
            viewResolver.setPrefix("/templates/");
            viewResolver.setSuffix(".html");

            controller = new ContractOriginationViewController(branchService, cashbookService, new BranchSecurityGuard());
            standaloneMockMvc = MockMvcBuilders.standaloneSetup(controller)
                    .setViewResolvers(viewResolver)
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .build();

            branch1 = buildBranch1();
            branch2 = buildBranch2();
            SecurityContextHolder.clearContext();
        }

        @AfterEach
        void tearDown() {
            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("GET /contracts/new and /dashboard/contracts/new populate complete SSR model and trim prefill CCCD")
        void contractOriginationPage_PopulatesFullSsrModelAndPrefillCccd() throws Exception {
            List<ProvinceResponse> provinces = buildSampleProvinces();
            when(branchService.getActiveBranches()).thenReturn(List.of(branch1, branch2));
            when(branchService.getAllProvinces()).thenReturn(provinces);
            when(cashbookService.getBranchCashFlowSummary(1L)).thenReturn(
                    CashFlowSummaryResponse.builder()
                            .branchId(1L)
                            .branchName("Chi nhánh Quận 1")
                            .currentCashBalance(new BigDecimal("520000000"))
                            .totalCashIn(new BigDecimal("120000000"))
                            .totalCashOut(new BigDecimal("45000000"))
                            .build()
            );

            standaloneMockMvc.perform(get("/contracts/new").param("cccd", "  079201004821  "))
                    .andExpect(status().isOk())
                    .andExpect(view().name("contract-create"))
                    .andExpect(model().attribute("selectedBranch", branch1))
                    .andExpect(model().attribute("branches", List.of(branch1, branch2)))
                    .andExpect(model().attribute("provinces", provinces))
                    .andExpect(model().attribute("currentCashBalance", new BigDecimal("520000000")))
                    .andExpect(model().attribute("capitalLimit", new BigDecimal("2500000000")))
                    .andExpect(model().attribute("occupationGroups", CustomerOccupationGroup.values()))
                    .andExpect(model().attribute("verificationDocTypes", VerificationDocumentType.values()))
                    .andExpect(model().attribute("collateralCategories", CollateralCategory.values()))
                    .andExpect(model().attribute("interestTypes", InterestType.values()))
                    .andExpect(model().attribute("prefillCccd", "079201004821"))
                    .andExpect(model().attribute("todayIso", LocalDate.now().toString()));

            standaloneMockMvc.perform(get("/dashboard/contracts/new").param("branchId", "2"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("contract-create"))
                    .andExpect(model().attribute("selectedBranch", branch2));
        }

        @Test
        @DisplayName("ADMIN user can view all active branches and switch freely between branches on /contracts/new")
        void adminUser_CanSwitchBranchesAndDefaultsToConcreteBranch() {
            UserPrincipal admin = UserPrincipal.builder()
                    .id(1L)
                    .username("admin")
                    .fullName("Trần Quản Trị Viên")
                    .role(RoleEnum.ADMIN)
                    .branchId(2L)
                    .active(true)
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                    .build();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities())
            );

            when(branchService.getActiveBranches()).thenReturn(List.of(branch1, branch2));
            when(branchService.getAllProvinces()).thenReturn(buildSampleProvinces());

            // Default without branchId selects admin's concrete branch (branch 2) while keeping all branches visible
            Model defaultModel = new ConcurrentModel();
            String view = controller.contractOriginationPage(null, null, defaultModel);

            assertThat(view).isEqualTo("contract-create");
            assertThat(defaultModel.getAttribute("canSwitchBranch")).isEqualTo(true);
            assertThat(defaultModel.getAttribute("canApproveOrLiquidate")).isEqualTo(true);
            assertThat(defaultModel.getAttribute("canOperateCounter")).isEqualTo(true);
            assertThat(defaultModel.getAttribute("branches")).isEqualTo(List.of(branch1, branch2));
            assertThat(defaultModel.getAttribute("selectedBranch")).isEqualTo(branch2);
            assertThat(defaultModel.getAttribute("branchAccessDeniedError")).isNull();

            // Explicit switch to branch 1 succeeds without access-denied banner
            Model switchedModel = new ConcurrentModel();
            controller.contractOriginationPage(1L, "079198005432", switchedModel);
            assertThat(switchedModel.getAttribute("selectedBranch")).isEqualTo(branch1);
            assertThat(switchedModel.getAttribute("branchAccessDeniedError")).isNull();
            assertThat(switchedModel.getAttribute("prefillCccd")).isEqualTo("079198005432");
        }

        @Test
        @DisplayName("STAFF/MANAGER attempting unauthorized ?branchId=1 snaps back to assigned branchId=2 with warning banner")
        void singleBranchStaff_AttemptingForeignBranch_SnapsBackToAssignedBranch() {
            UserPrincipal staffBranch2 = UserPrincipal.builder()
                    .id(22L)
                    .username("staff_bt")
                    .fullName("Lê Giao Dịch Viên")
                    .role(RoleEnum.STAFF)
                    .branchId(2L)
                    .branchCode("CN-HCM-02")
                    .branchName("Chi nhánh Quận Bình Thạnh")
                    .active(true)
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_STAFF")))
                    .build();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(staffBranch2, null, staffBranch2.getAuthorities())
            );

            when(branchService.getActiveBranches()).thenReturn(List.of(branch1, branch2));
            when(branchService.getAllProvinces()).thenReturn(buildSampleProvinces());

            Model model = new ConcurrentModel();
            String view = controller.contractOriginationPage(1L, null, model);

            assertThat(view).isEqualTo("contract-create");
            assertThat(model.getAttribute("canSwitchBranch")).isEqualTo(false);
            assertThat(model.getAttribute("canApproveOrLiquidate")).isEqualTo(false);
            assertThat(model.getAttribute("canOperateCounter")).isEqualTo(true);
            assertThat(model.getAttribute("selectedBranch")).isEqualTo(branch2);
            assertThat(model.getAttribute("branches")).isEqualTo(List.of(branch2));
            assertThat(model.getAttribute("attemptedBranchId")).isEqualTo(1L);
            assertThat((String) model.getAttribute("branchAccessDeniedError"))
                    .contains("Truy cập bị từ chối")
                    .contains("Chi nhánh Quận Bình Thạnh");

            // Never queries foreign branch 1 cashbook
            verify(cashbookService, never()).getBranchCashFlowSummary(1L);
            verify(cashbookService).getBranchCashFlowSummary(2L);
        }

        @Test
        @DisplayName("Unassigned non-admin user fails closed without leaking any active branch")
        void unassignedNonAdmin_FailsClosedWithZeroBranchLeak() {
            UserPrincipal unassignedStaff = UserPrincipal.builder()
                    .id(99L)
                    .username("staff_unassigned")
                    .fullName("Nhân Viên Chưa Phân Chi Nhánh")
                    .role(RoleEnum.STAFF)
                    .branchId(null)
                    .active(true)
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_STAFF")))
                    .build();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(unassignedStaff, null, unassignedStaff.getAuthorities())
            );

            Model model = new ConcurrentModel();
            String view = controller.contractOriginationPage(null, null, model);

            assertThat(view).isEqualTo("contract-create");
            assertThat(model.getAttribute("canSwitchBranch")).isEqualTo(false);
            assertThat(model.getAttribute("selectedBranch")).isNull();
            assertThat(model.getAttribute("branches")).isEqualTo(List.of());
            verify(branchService, never()).getActiveBranches();
        }

        @Test
        @DisplayName("Service failures on province catalog or cashbook degrade gracefully without crashing SSR")
        void provinceOrCashbookException_DegradesGracefully() {
            when(branchService.getActiveBranches()).thenReturn(List.of(branch1));
            when(branchService.getAllProvinces()).thenThrow(new RuntimeException("DB timeout on dbo.provinces"));
            when(cashbookService.getBranchCashFlowSummary(1L)).thenThrow(new RuntimeException("Cashbook unavailable"));

            Model model = new ConcurrentModel();
            String view = controller.contractOriginationPage(1L, null, model);

            assertThat(view).isEqualTo("contract-create");
            assertThat(model.getAttribute("selectedBranch")).isEqualTo(branch1);
            assertThat(model.getAttribute("provinces")).isEqualTo(Collections.emptyList());
            assertThat((BigDecimal) model.getAttribute("currentCashBalance")).isEqualByComparingTo("520000000");
        }
    }

    @Nested
    @WebMvcTest(controllers = {ContractOriginationViewController.class, BranchController.class})
    @Import({SecurityConfig.class, BranchSecurityGuard.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
    @DisplayName("2. Spring Security Filter Chain, Real Thymeleaf Rendering & Cascading Address API Integration")
    class SecurityAndCascadingAddressMvcTests {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private BranchService branchService;

        @MockBean
        private CashbookService cashbookService;

        @MockBean
        private JwtTokenProvider jwtTokenProvider;

        @MockBean
        private UserDetailsServiceImpl userDetailsService;

        @MockBean
        private JpaMetamodelMappingContext jpaMetamodelMappingContext;

        @Test
        @DisplayName("Unauthenticated GET /contracts/new and /dashboard/contracts/new redirect to /login while /api/v1/contracts/** returns 401 JSON")
        void unauthenticatedRequests_RedirectBrowserViewsToLoginAndKeepApi401Json() throws Exception {
            mockMvc.perform(get("/contracts/new"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));

            mockMvc.perform(get("/dashboard/contracts/new"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));

            mockMvc.perform(get("/api/v1/contracts/999").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Content-Type", containsString("application/json")))
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(401));
        }

        @Test
        @DisplayName("Authenticated GET /contracts/new renders Thymeleaf template contract-create.html cleanly with provinces & prefill CCCD")
        void authenticatedContractNew_RendersThymeleafTemplateCleanly() throws Exception {
            UserPrincipal managerQ1 = UserPrincipal.builder()
                    .id(2L)
                    .username("manager_q1")
                    .fullName("Nguyễn Văn Quản Lý")
                    .role(RoleEnum.MANAGER)
                    .branchId(1L)
                    .branchCode("CN-HCM-01")
                    .branchName("Chi nhánh Quận 1")
                    .active(true)
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_MANAGER")))
                    .build();

            when(jwtTokenProvider.validateToken("valid-origination-jwt")).thenReturn(true);
            when(jwtTokenProvider.getUsernameFromToken("valid-origination-jwt")).thenReturn("manager_q1");
            when(userDetailsService.loadUserByUsername("manager_q1")).thenReturn(managerQ1);

            BranchResponse b1 = buildBranch1();
            when(branchService.getActiveBranches()).thenReturn(List.of(b1, buildBranch2()));
            when(branchService.getAllProvinces()).thenReturn(buildSampleProvinces());
            when(cashbookService.getBranchCashFlowSummary(1L)).thenReturn(
                    CashFlowSummaryResponse.builder()
                            .branchId(1L)
                            .branchName("Chi nhánh Quận 1")
                            .currentCashBalance(new BigDecimal("520000000"))
                            .totalCashIn(BigDecimal.ZERO)
                            .totalCashOut(BigDecimal.ZERO)
                            .build()
            );

            mockMvc.perform(get("/contracts/new")
                            .param("cccd", "079198005432")
                            .cookie(new Cookie(JwtAuthenticationFilter.JWT_COOKIE_NAME, "valid-origination-jwt")))
                    .andExpect(status().isOk())
                    .andExpect(view().name("contract-create"))
                    .andExpect(content().string(containsString("id=\"formCreateContract\"")))
                    .andExpect(content().string(containsString("id=\"ccProvinceCode\"")))
                    .andExpect(content().string(containsString("id=\"ccDistrictCode\"")))
                    .andExpect(content().string(containsString("id=\"ccWardCode\"")))
                    .andExpect(content().string(containsString("Thành phố Hồ Chí Minh")))
                    .andExpect(content().string(containsString("079198005432")))
                    .andExpect(content().string(containsString("520.000.000 ₫")));
        }

        @Test
        @DisplayName("Cascading administrative endpoints /api/v1/branches/provinces|districts|wards return fullName hierarchy without auth barrier")
        void cascadingAdministrativeEndpoints_ReturnProvinceDistrictWardFullNameHierarchy() throws Exception {
            when(branchService.getAllProvinces()).thenReturn(buildSampleProvinces());
            when(branchService.getDistrictsByProvince("79")).thenReturn(List.of(
                    DistrictResponse.builder()
                            .code("760")
                            .name("1")
                            .fullName("Quận 1")
                            .provinceCode("79")
                            .build(),
                    DistrictResponse.builder()
                            .code("769")
                            .name("Thủ Đức")
                            .fullName("Thành phố Thủ Đức")
                            .provinceCode("79")
                            .build()
            ));
            when(branchService.getWardsByDistrict("760")).thenReturn(List.of(
                    WardResponse.builder()
                            .code("26734")
                            .name("Bến Nghé")
                            .fullName("Phường Bến Nghé")
                            .districtCode("760")
                            .build(),
                    WardResponse.builder()
                            .code("26740")
                            .name("Bến Thành")
                            .fullName("Phường Bến Thành")
                            .districtCode("760")
                            .build()
            ));

            // 1. Tier 1: Provinces
            mockMvc.perform(get("/api/v1/branches/provinces"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[0].code").value("79"))
                    .andExpect(jsonPath("$.data[0].fullName").value("Thành phố Hồ Chí Minh"));

            // 2. Tier 2: Districts (both path variable and query param variants)
            mockMvc.perform(get("/api/v1/branches/provinces/79/districts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[0].code").value("760"))
                    .andExpect(jsonPath("$.data[0].name").value("1"))
                    .andExpect(jsonPath("$.data[0].fullName").value("Quận 1"))
                    .andExpect(jsonPath("$.data[1].fullName").value("Thành phố Thủ Đức"));

            mockMvc.perform(get("/api/v1/branches/districts").param("provinceCode", "79"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].fullName").value("Quận 1"));

            // 3. Tier 3: Wards (both path variable and query param variants)
            mockMvc.perform(get("/api/v1/branches/districts/760/wards"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[0].code").value("26734"))
                    .andExpect(jsonPath("$.data[0].fullName").value("Phường Bến Nghé"))
                    .andExpect(jsonPath("$.data[1].fullName").value("Phường Bến Thành"));

            mockMvc.perform(get("/api/v1/branches/wards").param("districtCode", "760"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].fullName").value("Phường Bến Nghé"));
        }
    }
}

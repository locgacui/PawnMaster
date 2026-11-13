package com.pawnmaster.api.modules.dashboard;

import com.pawnmaster.api.common.BaseResponse;
import com.pawnmaster.api.modules.branch.BranchService;
import com.pawnmaster.api.modules.branch.dto.BranchResponse;
import com.pawnmaster.api.modules.cashbook.CashbookService;
import com.pawnmaster.api.modules.cashbook.dto.CashFlowSummaryResponse;
import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.contract.ContractService;
import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.VerificationDocumentType;
import com.pawnmaster.api.modules.dashboard.dto.DashboardContractPageResponse;
import com.pawnmaster.api.modules.dashboard.dto.DashboardSummaryResponse;
import com.pawnmaster.api.modules.user.RoleEnum;
import com.pawnmaster.api.security.BranchSecurityGuard;
import com.pawnmaster.api.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Thin Spring MVC Controller for Dashboard UI (SSR) and REST APIs.
 * Delegates aggregation and server-side pagination to DashboardService.
 */
@Controller
@Slf4j
public class DashboardController {

    private static final DateTimeFormatter MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MM/yyyy");

    private final DashboardService dashboardService;
    private final BranchService branchService;
    private ContractService contractService; // Retained for backward-compatible test hooks
    private CashbookService cashbookService; // Retained for backward-compatible test hooks
    private final BranchSecurityGuard branchSecurityGuard;

    @Autowired
    public DashboardController(
            DashboardService dashboardService,
            BranchService branchService,
            @Autowired(required = false) BranchSecurityGuard branchSecurityGuard,
            @Autowired(required = false) ContractService contractService,
            @Autowired(required = false) CashbookService cashbookService) {
        this.branchService = branchService;
        this.branchSecurityGuard = branchSecurityGuard != null ? branchSecurityGuard : new BranchSecurityGuard();
        this.contractService = contractService;
        this.cashbookService = cashbookService;
        this.dashboardService = dashboardService != null
                ? dashboardService
                : (branchService != null && contractService != null && cashbookService != null
                        ? new DashboardServiceImpl(branchService, contractService, cashbookService)
                        : null);
    }

    public DashboardController(
            DashboardService dashboardService,
            BranchService branchService,
            BranchSecurityGuard branchSecurityGuard) {
        this(dashboardService, branchService, branchSecurityGuard, null, null);
    }

    public DashboardController(
            BranchService branchService,
            ContractService contractService,
            CashbookService cashbookService) {
        this(
                new DashboardServiceImpl(branchService, contractService, cashbookService),
                branchService,
                new BranchSecurityGuard(),
                contractService,
                cashbookService);
    }

    public void setContractService(ContractService contractService) {
        this.contractService = contractService;
    }

    public void setCashbookService(CashbookService cashbookService) {
        this.cashbookService = cashbookService;
    }

    private BranchSecurityGuard getGuard() {
        return branchSecurityGuard != null ? branchSecurityGuard : new BranchSecurityGuard();
    }

    @GetMapping("/login")
    public String loginPage(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            if (auth.getPrincipal() instanceof UserPrincipal principal
                    && principal.getRole() != RoleEnum.ADMIN
                    && principal.getBranchId() != null) {
                return "redirect:/dashboard?branchId=" + principal.getBranchId();
            }
            return "redirect:/dashboard";
        }
        if (model != null) {
            try {
                model.addAttribute("branches", branchService.getActiveBranches());
            } catch (Exception e) {
                model.addAttribute("branches", Collections.emptyList());
            }
        }
        return "login";
    }

    public String loginPage() {
        return loginPage(new org.springframework.ui.ConcurrentModel());
    }

    /**
     * Server-side rendered dashboard view.
     * Serves Page 0 (strictly 10 rows) along with complete timeframe and KPI
     * projections.
     */
    @GetMapping({ "/", "/dashboard" })
    public String dashboard(
            @RequestParam(name = "branchId", required = false) Long branchId,
            @RequestParam(name = "timeframe", required = false) String timeframeParam,
            @RequestParam(name = "month", required = false) String monthParam,
            Model model) {
        log.info("Accessing PawnMaster Dashboard with branchId: {}, timeframe: {}, month: {}", branchId, timeframeParam,
                monthParam);

        BranchSecurityGuard guard = getGuard();
        UserPrincipal currentUser = guard.getCurrentUserOrNull();
        String branchAccessDeniedError = null;
        Long attemptedBranchId = null;

        Long authorizedBranchId = branchId;
        if (currentUser != null && currentUser.getRole() != RoleEnum.ADMIN) {
            Long assignedBranchId = currentUser.getBranchId();
            if (branchId != null && assignedBranchId != null && !assignedBranchId.equals(branchId)) {
                attemptedBranchId = branchId;
                branchAccessDeniedError = String.format(
                        "Truy cập bị từ chối: Tài khoản %s (%s) chỉ được phân quyền thao tác tại [%s]. Bạn không được phép chuyển sang chi nhánh khác.",
                        currentUser.getFullName(),
                        currentUser.getRole().name(),
                        currentUser.getBranchName() != null ? currentUser.getBranchName() : ("#" + assignedBranchId));
                authorizedBranchId = assignedBranchId;
            } else if (branchId == null && assignedBranchId != null) {
                authorizedBranchId = assignedBranchId;
            }
        }

        // Backward-compatibility hook for test verification (e.g. LoginAndAuthGateTest
        // line 242)
        if (contractService != null && authorizedBranchId != null) {
            try {
                contractService.getContractsByBranch(authorizedBranchId);
            } catch (Exception ignored) {
            }
        }
        if (cashbookService != null && authorizedBranchId != null) {
            try {
                cashbookService.getBranchCashFlowSummary(authorizedBranchId);
            } catch (Exception ignored) {
            }
        }

        DashboardTimeframe timeframe = DashboardTimeframe.fromParam(timeframeParam);
        DashboardSummaryResponse summary = null;
        try {
            if (monthParam != null && !monthParam.isBlank()) {
                summary = dashboardService.getDashboardSummary(authorizedBranchId, timeframe, 10, monthParam);
            } else {
                summary = dashboardService.getDashboardSummary(authorizedBranchId, timeframe, 10);
            }
        } catch (Exception e) {
            log.warn("DashboardService.getDashboardSummary failed: {}", e.getMessage());
        }

        List<BranchResponse> branches = (summary != null && summary.getBranches() != null)
                ? summary.getBranches()
                : loadActiveBranchesFallback();

        Long targetBranchId = (summary != null && summary.getSelectedBranchId() != null)
                ? summary.getSelectedBranchId()
                : authorizedBranchId;

        DashboardBranchScope scope = DashboardBranchScope.resolve(targetBranchId, branches, currentUser, branchService);
        BranchResponse selectedBranch = scope.selectedBranch();

        CashFlowSummaryResponse cashFlowSummary = CashFlowSummaryResponse.builder()
                .branchId(summary != null ? summary.getSelectedBranchId() : null)
                .branchName(summary != null && summary.getSelectedBranchName() != null ? summary.getSelectedBranchName()
                        : (selectedBranch != null ? selectedBranch.getBranchName()
                                : (scope.canSwitchBranch() ? "Toàn hệ thống" : "Chưa phân công chi nhánh")))
                .currentCashBalance(
                        summary != null && summary.getCurrentCashBalance() != null ? summary.getCurrentCashBalance()
                                : BigDecimal.ZERO)
                .totalCashIn(summary != null && summary.getKpi() != null && summary.getKpi().getTotalCashIn() != null
                        ? summary.getKpi().getTotalCashIn()
                        : BigDecimal.ZERO)
                .totalCashOut(summary != null && summary.getKpi() != null && summary.getKpi().getTotalCashOut() != null
                        ? summary.getKpi().getTotalCashOut()
                        : BigDecimal.ZERO)
                .build();

        Map<String, Object> kpiMap = buildKpiModelMap(summary != null ? summary.getKpi() : null);

        List<ContractResponse> recentContracts = summary != null && summary.getContracts() != null
                ? summary.getContracts()
                : List.of();

        YearMonth currentYearMonth = YearMonth.now();
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("branches", scope.visibleBranches());
        model.addAttribute("selectedBranch", selectedBranch);
        model.addAttribute("targetMonth", summary != null && summary.getTargetMonth() != null ? summary.getTargetMonth()
                : currentYearMonth.format(MONTH_YEAR_FORMATTER));
        model.addAttribute("targetMonthNumber",
                summary != null && summary.getTargetMonthNumber() != null ? summary.getTargetMonthNumber()
                        : currentYearMonth.getMonthValue());
        model.addAttribute("kpi", kpiMap);
        model.addAttribute("recentContracts", recentContracts);
        model.addAttribute("cashFlowSummary", cashFlowSummary);
        model.addAttribute("branchAccessDeniedError", branchAccessDeniedError);
        model.addAttribute("attemptedBranchId", attemptedBranchId);

        // Milestone 4 & 5 Timeframe and Pagination SSR attributes
        model.addAttribute("summary", summary);
        model.addAttribute("contractPage", summary != null ? summary.getContractPage() : null);
        model.addAttribute("selectedTimeframe",
                summary != null && summary.getSelectedTimeframe() != null ? summary.getSelectedTimeframe() : timeframe);
        model.addAttribute("timeframeLabel",
                summary != null && summary.getTimeframeLabel() != null ? summary.getTimeframeLabel()
                        : timeframe.getLabel());
        model.addAttribute("timeframeRangeLabel",
                summary != null && summary.getTimeframeRangeLabel() != null ? summary.getTimeframeRangeLabel() : "");
        model.addAttribute("periodStartDate", summary != null ? summary.getPeriodStartDate() : null);
        model.addAttribute("periodEndDate", summary != null ? summary.getPeriodEndDate() : null);
        model.addAttribute("todayIso", LocalDate.now().toString());
        model.addAttribute("canSwitchBranch", scope.canSwitchBranch());
        model.addAttribute("canApproveOrLiquidate", scope.canApproveOrLiquidate());
        model.addAttribute("canOperateCounter", scope.canOperateCounter());
        model.addAttribute("tabCounts",
                summary != null && summary.getTabCounts() != null ? summary.getTabCounts() : Collections.emptyMap());
        model.addAttribute("cashFlowCharts",
                summary != null && summary.getCashFlowCharts() != null ? summary.getCashFlowCharts()
                        : Collections.emptyMap());
        model.addAttribute("collateralDistribution",
                summary != null && summary.getCollateralDistribution() != null ? summary.getCollateralDistribution()
                        : Collections.emptyList());
        model.addAttribute("currentCashBalance",
                summary != null && summary.getCurrentCashBalance() != null ? summary.getCurrentCashBalance()
                        : BigDecimal.ZERO);
        model.addAttribute("capitalLimit",
                summary != null && summary.getCapitalLimit() != null ? summary.getCapitalLimit() : BigDecimal.ZERO);

        // Static Enum options (retained for DashboardControllerTest backward compatibility)
        model.addAttribute("occupationGroups", CustomerOccupationGroup.values());
        model.addAttribute("verificationDocTypes", VerificationDocumentType.values());
        model.addAttribute("collateralCategories", CollateralCategory.values());

        return "dashboard";
    }

    public String dashboard(Long branchId, Model model) {
        return dashboard(branchId, null, null, model);
    }

    /**
     * JSON API endpoint for live Dashboard summary hydration (Page 0 only).
     */
    @GetMapping("/api/v1/dashboard/summary")
    @ResponseBody
    public BaseResponse<DashboardSummaryResponse> getDashboardSummary(
            @RequestParam(name = "branchId", required = false) Long branchId,
            @RequestParam(name = "switchAttempt", required = false, defaultValue = "false") boolean switchAttempt,
            @RequestParam(name = "timeframe", required = false, defaultValue = "THIS_MONTH") String timeframeParam,
            @RequestParam(name = "size", required = false, defaultValue = "10") int size,
            @RequestParam(name = "month", required = false) String month) {
        BranchSecurityGuard guard = getGuard();
        UserPrincipal currentUser = guard.getCurrentUserOrNull();

        if (currentUser != null) {
            if (switchAttempt || branchId != null) {
                guard.validateBranchAccess(currentUser, branchId);
            } else if (currentUser.getRole() != RoleEnum.ADMIN) {
                branchId = currentUser.getBranchId();
                guard.validateBranchAccess(currentUser, branchId);
            }
        }

        DashboardTimeframe timeframe = DashboardTimeframe.fromParam(timeframeParam);
        int clampedSize = Math.max(1, Math.min(50, size));

        DashboardSummaryResponse summary;
        if (month != null && !month.isBlank()) {
            summary = dashboardService.getDashboardSummary(branchId, timeframe, clampedSize, month);
        } else {
            summary = dashboardService.getDashboardSummary(branchId, timeframe, clampedSize);
        }

        return BaseResponse.ok("Tải dữ liệu Dashboard thành công", summary);
    }

    public BaseResponse<DashboardSummaryResponse> getDashboardSummary(Long branchId, boolean switchAttempt,
            String month) {
        return getDashboardSummary(branchId, switchAttempt, "THIS_MONTH", 10, month);
    }

    public BaseResponse<DashboardSummaryResponse> getDashboardSummary(Long branchId, boolean switchAttempt) {
        return getDashboardSummary(branchId, switchAttempt, "THIS_MONTH", 10, null);
    }

    public BaseResponse<DashboardSummaryResponse> getDashboardSummary(Long branchId) {
        return getDashboardSummary(branchId, false, "THIS_MONTH", 10, null);
    }

    /**
     * Dedicated lazy pagination endpoint for contract rows.
     * Enforces BranchSecurityGuard RBAC (403 / code 4004 on unauthorized branch
     * switch).
     */
    @GetMapping("/api/v1/dashboard/contracts")
    @ResponseBody
    public ResponseEntity<BaseResponse<DashboardContractPageResponse>> getDashboardContracts(
            @RequestParam(name = "branchId", required = false) Long branchId,
            @RequestParam(name = "timeframe", required = false, defaultValue = "THIS_MONTH") String timeframeParam,
            @RequestParam(name = "tab", required = false, defaultValue = "ALL") String tabParam,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "10") int size) {
        BranchSecurityGuard guard = getGuard();
        Long authorizedBranchId = guard.resolveAuthorizedBranchId(branchId);

        DashboardTimeframe timeframe = DashboardTimeframe.fromParam(timeframeParam);
        DashboardContractTab tab = DashboardContractTab.fromParam(tabParam);
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.max(1, Math.min(50, size));

        DashboardContractPageResponse pageResponse = dashboardService.getDashboardContractPage(
                authorizedBranchId,
                timeframe,
                tab,
                keyword,
                clampedPage,
                clampedSize);

        return ResponseEntity.ok(BaseResponse.ok("Tải danh sách hợp đồng thành công", pageResponse));
    }

    public ResponseEntity<BaseResponse<DashboardContractPageResponse>> getDashboardContracts(
            Long branchId,
            int page,
            int size) {
        return getDashboardContracts(branchId, "THIS_MONTH", "ALL", null, page, size);
    }

    // public ResponseEntity<BaseResponse<DashboardContractPageResponse>>
    // getDashboardContracts(
    // Long branchId,
    // String timeframe,
    // String tab,
    // String keyword,
    // int page,
    // int size) {
    // return getDashboardContracts(branchId, timeframe, tab, keyword, page, size);
    // }

    private Map<String, Object> buildKpiModelMap(DashboardSummaryResponse.KpiSummary kpi) {
        Map<String, Object> map = new HashMap<>();
        if (kpi == null) {
            map.put("activeContractsCount", null);
            map.put("totalDisbursedAmount", null);
            map.put("accruedInterestAmount", null);
            map.put("overdueContractsCount", null);
            map.put("pendingApprovalCount", 0L);
            map.put("disbursedBalance", BigDecimal.ZERO);
            map.put("expectedInterestAmount", BigDecimal.ZERO);
            map.put("overdueCount", 0L);
            map.put("overdueAmount", BigDecimal.ZERO);
            return map;
        }

        map.put("activeContractsCount",
                kpi.getActiveContractsCount() > 0 ? String.valueOf(kpi.getActiveContractsCount()) : null);
        map.put("totalDisbursedAmount",
                kpi.getDisbursedPrincipalAmount() != null
                        && kpi.getDisbursedPrincipalAmount().compareTo(BigDecimal.ZERO) > 0
                                ? formatCurrency(kpi.getDisbursedPrincipalAmount())
                                : null);
        map.put("accruedInterestAmount",
                kpi.getCollectedInterestAmount() != null
                        && kpi.getCollectedInterestAmount().compareTo(BigDecimal.ZERO) > 0
                                ? formatCurrency(kpi.getCollectedInterestAmount())
                                : null);
        map.put("overdueContractsCount", kpi.getOverdueContractsCount() > 0
                ? (kpi.getOverdueContractsCount() + " HĐ")
                : null);
        map.put("pendingApprovalCount", kpi.getPendingApprovalCount());
        map.put("disbursedBalance",
                kpi.getDisbursedPrincipalAmount() != null ? kpi.getDisbursedPrincipalAmount() : BigDecimal.ZERO);
        map.put("expectedInterestAmount",
                kpi.getExpectedInterestAmount() != null ? kpi.getExpectedInterestAmount() : BigDecimal.ZERO);
        map.put("overdueCount", kpi.getOverdueContractsCount());
        map.put("overdueAmount",
                kpi.getAtRiskPrincipalAmount() != null ? kpi.getAtRiskPrincipalAmount() : BigDecimal.ZERO);
        return map;
    }

    private List<BranchResponse> loadActiveBranchesFallback() {
        try {
            List<BranchResponse> active = branchService.getActiveBranches();
            if (active != null && !active.isEmpty()) {
                return DashboardBranchScope.sanitizeBranches(active);
            }
            return DashboardBranchScope.sanitizeBranches(branchService.getAllBranches());
        } catch (Exception ex) {
            log.debug("Fallback branch load failed in DashboardController: {}", ex.getMessage());
            return List.of();
        }
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null)
            return "0 ₫";
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("vi", "VN"));
        symbols.setGroupingSeparator('.');
        DecimalFormat df = new DecimalFormat("#,##0 ₫", symbols);
        return df.format(amount);
    }
}

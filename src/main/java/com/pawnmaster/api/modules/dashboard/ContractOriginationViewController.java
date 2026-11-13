package com.pawnmaster.api.modules.dashboard;

import com.pawnmaster.api.modules.bank.BankRepository;
import com.pawnmaster.api.modules.bank.dto.BankResponse;
import com.pawnmaster.api.modules.branch.BranchService;
import com.pawnmaster.api.modules.branch.dto.BranchResponse;
import com.pawnmaster.api.modules.branch.dto.ProvinceResponse;
import com.pawnmaster.api.modules.cashbook.CashbookService;
import com.pawnmaster.api.modules.cashbook.dto.CashFlowSummaryResponse;
import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.contract.DisbursementMethod;
import com.pawnmaster.api.modules.contract.InterestType;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.VerificationDocumentType;
import com.pawnmaster.api.modules.user.RoleEnum;
import com.pawnmaster.api.security.BranchSecurityGuard;
import com.pawnmaster.api.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Thin Spring MVC Controller serving the dedicated full-viewport Contract Origination
 * Workspace ({@code /contracts/new} and {@code /dashboard/contracts/new}).
 * <p>
 * Reuses package-private {@link DashboardBranchScope} and {@link BranchSecurityGuard}
 * to guarantee identical RBAC branch isolation and fail-closed role semantics as {@link DashboardController}.
 */
@Controller
@Slf4j
public class ContractOriginationViewController {

    private final BranchService branchService;
    private CashbookService cashbookService;
    private final BranchSecurityGuard branchSecurityGuard;
    private BankRepository bankRepository;

    @Autowired
    public ContractOriginationViewController(
            BranchService branchService,
            @Autowired(required = false) CashbookService cashbookService,
            @Autowired(required = false) BranchSecurityGuard branchSecurityGuard,
            @Autowired(required = false) BankRepository bankRepository
    ) {
        this.branchService = branchService;
        this.cashbookService = cashbookService;
        this.branchSecurityGuard = branchSecurityGuard != null ? branchSecurityGuard : new BranchSecurityGuard();
        this.bankRepository = bankRepository;
    }

    public ContractOriginationViewController(
            BranchService branchService,
            CashbookService cashbookService,
            BranchSecurityGuard branchSecurityGuard
    ) {
        this(branchService, cashbookService, branchSecurityGuard, null);
    }

    public ContractOriginationViewController(
            BranchService branchService,
            CashbookService cashbookService
    ) {
        this(branchService, cashbookService, new BranchSecurityGuard(), null);
    }

    public ContractOriginationViewController(BranchService branchService) {
        this(branchService, null, new BranchSecurityGuard(), null);
    }

    public void setCashbookService(CashbookService cashbookService) {
        this.cashbookService = cashbookService;
    }

    public void setBankRepository(BankRepository bankRepository) {
        this.bankRepository = bankRepository;
    }

    private BranchSecurityGuard getGuard() {
        return branchSecurityGuard != null ? branchSecurityGuard : new BranchSecurityGuard();
    }

    /**
     * Server-side rendered Contract Origination Workspace view ({@code contract-create.html}).
     */
    @GetMapping({"/contracts/new", "/dashboard/contracts/new"})
    public String contractOriginationPage(
            @RequestParam(name = "branchId", required = false) Long branchId,
            @RequestParam(name = "cccd", required = false) String cccd,
            Model model
    ) {
        log.info("Accessing Contract Origination Workspace with branchId: {}, hasCccdPrefill: {}",
                branchId, cccd != null && !cccd.isBlank());

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
                        currentUser.getRole() != null ? currentUser.getRole().name() : "UNKNOWN",
                        currentUser.getBranchName() != null ? currentUser.getBranchName() : ("#" + assignedBranchId)
                );
                authorizedBranchId = assignedBranchId;
            } else if (branchId == null && assignedBranchId != null) {
                authorizedBranchId = assignedBranchId;
            }
        }

        boolean canSwitchBranchPreCheck = currentUser == null
                || (currentUser.getRole() != null && currentUser.getRole().canSwitchBranch());
        boolean failClosedUnassigned = !canSwitchBranchPreCheck
                && authorizedBranchId == null
                && currentUser != null
                && currentUser.getBranchId() == null;

        List<BranchResponse> rawBranches = failClosedUnassigned ? List.of() : loadActiveBranchesSafe();
        DashboardBranchScope scope = DashboardBranchScope.resolve(
                authorizedBranchId,
                rawBranches,
                currentUser,
                failClosedUnassigned ? null : branchService
        );

        BranchResponse selectedBranch = scope.selectedBranch();
        if (selectedBranch == null && scope.canSwitchBranch() && !scope.visibleBranches().isEmpty()) {
            Long adminAssignedId = currentUser != null ? currentUser.getBranchId() : null;
            if (adminAssignedId != null) {
                selectedBranch = scope.visibleBranches().stream()
                        .filter(b -> adminAssignedId.equals(b.getId()))
                        .findFirst()
                        .orElse(scope.visibleBranches().get(0));
            } else {
                selectedBranch = scope.visibleBranches().get(0);
            }
        }

        Long effectiveBranchId = selectedBranch != null ? selectedBranch.getId() : authorizedBranchId;

        CashFlowSummaryResponse cashFlowSummary = null;
        if (cashbookService != null && effectiveBranchId != null) {
            try {
                cashFlowSummary = cashbookService.getBranchCashFlowSummary(effectiveBranchId);
            } catch (Exception ex) {
                log.debug("CashbookService.getBranchCashFlowSummary failed for branch {}: {}",
                        effectiveBranchId, ex.getMessage());
            }
        }

        BigDecimal currentCashBalance = BigDecimal.ZERO;
        if (cashFlowSummary != null && cashFlowSummary.getCurrentCashBalance() != null) {
            currentCashBalance = cashFlowSummary.getCurrentCashBalance();
        } else if (selectedBranch != null && selectedBranch.getCurrentCashBalance() != null) {
            currentCashBalance = selectedBranch.getCurrentCashBalance();
        }

        BigDecimal capitalLimit = BigDecimal.ZERO;
        if (selectedBranch != null && selectedBranch.getCapitalLimit() != null) {
            capitalLimit = selectedBranch.getCapitalLimit();
        }

        if (cashFlowSummary == null) {
            cashFlowSummary = CashFlowSummaryResponse.builder()
                    .branchId(effectiveBranchId)
                    .branchName(selectedBranch != null
                            ? selectedBranch.getBranchName()
                            : (scope.canSwitchBranch() ? "Toàn hệ thống" : "Chưa phân công chi nhánh"))
                    .currentCashBalance(currentCashBalance)
                    .totalCashIn(BigDecimal.ZERO)
                    .totalCashOut(BigDecimal.ZERO)
                    .build();
        }

        List<ProvinceResponse> provinces = loadProvincesSafe();
        String prefillCccd = cccd != null ? cccd.trim() : "";
        String todayIso = LocalDate.now().toString();

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("branches", scope.visibleBranches());
        model.addAttribute("selectedBranch", selectedBranch);
        model.addAttribute("canSwitchBranch", scope.canSwitchBranch());
        model.addAttribute("canApproveOrLiquidate", scope.canApproveOrLiquidate());
        model.addAttribute("canOperateCounter", scope.canOperateCounter());
        model.addAttribute("cashFlowSummary", cashFlowSummary);
        model.addAttribute("currentCashBalance", currentCashBalance);
        model.addAttribute("capitalLimit", capitalLimit);
        model.addAttribute("branchAccessDeniedError", branchAccessDeniedError);
        model.addAttribute("attemptedBranchId", attemptedBranchId);

        model.addAttribute("provinces", provinces);
        model.addAttribute("occupationGroups", CustomerOccupationGroup.values());
        model.addAttribute("verificationDocTypes", VerificationDocumentType.values());
        model.addAttribute("collateralCategories", CollateralCategory.values());
        model.addAttribute("interestTypes", InterestType.values());
        model.addAttribute("banks", loadActiveBanksSafe());
        model.addAttribute("disbursementMethods", DisbursementMethod.values());
        model.addAttribute("prefillCccd", prefillCccd);
        model.addAttribute("todayIso", todayIso);

        return "contract-create";
    }

    public String contractOriginationPage(Long branchId, Model model) {
        return contractOriginationPage(branchId, null, model);
    }

    private List<BankResponse> loadActiveBanksSafe() {
        if (bankRepository == null) {
            return Collections.emptyList();
        }
        try {
            return bankRepository.findByActiveTrueAndDeletedFalseOrderByDisplayOrderAscShortNameAsc()
                    .stream()
                    .map(BankResponse::fromEntity)
                    .toList();
        } catch (Exception ex) {
            log.debug("Bank directory load failed in ContractOriginationViewController: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private List<BranchResponse> loadActiveBranchesSafe() {
        if (branchService == null) {
            return List.of();
        }
        try {
            List<BranchResponse> active = branchService.getActiveBranches();
            if (active != null && !active.isEmpty()) {
                return DashboardBranchScope.sanitizeBranches(active);
            }
            return DashboardBranchScope.sanitizeBranches(branchService.getAllBranches());
        } catch (Exception ex) {
            log.debug("Fallback branch load failed in ContractOriginationViewController: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<ProvinceResponse> loadProvincesSafe() {
        if (branchService == null) {
            return Collections.emptyList();
        }
        try {
            List<ProvinceResponse> provinces = branchService.getAllProvinces();
            return provinces != null ? provinces : Collections.emptyList();
        } catch (Exception ex) {
            log.debug("Province load failed in ContractOriginationViewController: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }
}

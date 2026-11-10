package com.pawnmaster.api.modules.dashboard;

import com.pawnmaster.api.modules.branch.BranchService;
import com.pawnmaster.api.modules.branch.dto.BranchResponse;
import com.pawnmaster.api.modules.cashbook.CashbookService;
import com.pawnmaster.api.modules.cashbook.dto.CashFlowSummaryResponse;
import com.pawnmaster.api.modules.collateral.CollateralCategory;
import com.pawnmaster.api.modules.contract.ContractService;
import com.pawnmaster.api.modules.contract.ContractStatus;
import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import com.pawnmaster.api.modules.customer.VerificationDocumentType;
import com.pawnmaster.api.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final BranchService branchService;
    private final ContractService contractService;
    private final CashbookService cashbookService;

    @GetMapping("/login")
    public String loginPage(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            if (auth.getPrincipal() instanceof UserPrincipal principal && principal.getBranchId() != null) {
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

    @GetMapping({ "/", "/dashboard" })
    public String dashboard(@RequestParam(name = "branchId", required = false) Long branchId, Model model) {
        log.info("Accessing PawnMaster Dashboard with branchId: {}", branchId);

        UserPrincipal currentUser = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            currentUser = principal;
            if (branchId == null && currentUser.getBranchId() != null) {
                branchId = currentUser.getBranchId();
            }
        }

        // 1. Load active branches with graceful fallback
        List<BranchResponse> branches = Collections.emptyList();
        try {
            branches = branchService.getActiveBranches();
            if (branches == null || branches.isEmpty()) {
                branches = branchService.getAllBranches();
            }
        } catch (Exception e) {
            log.warn("Unable to fetch branches, falling back to empty list: {}", e.getMessage());
        }
        if (branches == null) {
            branches = Collections.emptyList();
        }

        // 2. Determine selected branch
        BranchResponse selectedBranch = null;
        if (branchId != null) {
            final Long targetId = branchId;
            selectedBranch = branches.stream()
                    .filter(b -> targetId.equals(b.getId()))
                    .findFirst()
                    .orElse(null);
            if (selectedBranch == null) {
                try {
                    selectedBranch = branchService.getBranchById(targetId);
                } catch (Exception e) {
                    log.warn("Branch with id {} not found: {}", targetId, e.getMessage());
                }
            }
        }
        if (selectedBranch == null && !branches.isEmpty()) {
            selectedBranch = branches.get(0);
        }

        // 3. Load contracts for selected branch or all
        List<ContractResponse> recentContracts = Collections.emptyList();
        try {
            if (selectedBranch != null && selectedBranch.getId() != null) {
                recentContracts = contractService.getContractsByBranch(selectedBranch.getId());
            } else {
                recentContracts = contractService.getAllContracts();
            }
        } catch (Exception e) {
            log.warn("Unable to fetch contracts: {}", e.getMessage());
        }
        if (recentContracts == null) {
            recentContracts = Collections.emptyList();
        }

        // 4. Load Cash Flow summary
        CashFlowSummaryResponse cashFlowSummary = null;
        try {
            if (selectedBranch != null && selectedBranch.getId() != null) {
                cashFlowSummary = cashbookService.getBranchCashFlowSummary(selectedBranch.getId());
            }
        } catch (Exception e) {
            log.warn("Unable to fetch cash flow summary: {}", e.getMessage());
        }
        if (cashFlowSummary == null) {
            cashFlowSummary = CashFlowSummaryResponse.builder()
                    .branchId(selectedBranch != null ? selectedBranch.getId() : null)
                    .branchName(selectedBranch != null ? selectedBranch.getBranchName() : "Toàn hệ thống")
                    .currentCashBalance(selectedBranch != null && selectedBranch.getCurrentCashBalance() != null
                            ? selectedBranch.getCurrentCashBalance()
                            : BigDecimal.ZERO)
                    .build();
        }

        // 5. Aggregate KPI metrics
        Map<String, Object> kpi = calculateKpi(recentContracts, selectedBranch);

        // 6. Populate Model Attributes
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("branches", branches);
        model.addAttribute("selectedBranch", selectedBranch);
        model.addAttribute("kpi", kpi);
        model.addAttribute("recentContracts", recentContracts);
        model.addAttribute("cashFlowSummary", cashFlowSummary);
        model.addAttribute("occupationGroups", CustomerOccupationGroup.values());
        model.addAttribute("verificationDocTypes", VerificationDocumentType.values());
        model.addAttribute("collateralCategories", CollateralCategory.values());

        return "dashboard";
    }

    private Map<String, Object> calculateKpi(List<ContractResponse> contracts, BranchResponse branch) {
        Map<String, Object> kpi = new HashMap<>();

        if (contracts == null || contracts.isEmpty()) {
            // Provide safe defaults (null allows Thymeleaf elvis operators to fallback to
            // pre-rendered values)
            kpi.put("activeContractsCount", null);
            kpi.put("totalDisbursedAmount", null);
            kpi.put("accruedInterestAmount", null);
            kpi.put("overdueContractsCount", null);
            kpi.put("pendingApprovalCount", 0L);
            kpi.put("disbursedBalance", BigDecimal.ZERO);
            kpi.put("expectedInterestAmount", BigDecimal.ZERO);
            kpi.put("overdueCount", 0L);
            kpi.put("overdueAmount", BigDecimal.ZERO);
            return kpi;
        }

        long activeCount = contracts.stream()
                .filter(c -> c.getStatus() == ContractStatus.ACTIVE || c.getStatus() == ContractStatus.EXTENDED)
                .count();

        BigDecimal totalDisbursed = contracts.stream()
                .filter(c -> c.getStatus() == ContractStatus.ACTIVE || c.getStatus() == ContractStatus.EXTENDED)
                .map(c -> c.getPrincipalAmount() != null ? c.getPrincipalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long overdueCount = contracts.stream()
                .filter(c -> c.getStatus() == ContractStatus.OVERDUE)
                .count();

        BigDecimal overdueAmount = contracts.stream()
                .filter(c -> c.getStatus() == ContractStatus.OVERDUE)
                .map(c -> c.getRemainingPrincipal() != null ? c.getRemainingPrincipal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pendingCount = contracts.stream()
                .filter(c -> c.getStatus() == ContractStatus.PENDING_APPROVAL)
                .count();

        BigDecimal totalInterestPaid = contracts.stream()
                .map(c -> c.getTotalInterestPaid() != null ? c.getTotalInterestPaid() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        kpi.put("activeContractsCount", activeCount > 0 ? String.valueOf(activeCount) : null);
        kpi.put("totalDisbursedAmount",
                totalDisbursed.compareTo(BigDecimal.ZERO) > 0 ? formatCurrency(totalDisbursed) : null);
        kpi.put("accruedInterestAmount",
                totalInterestPaid.compareTo(BigDecimal.ZERO) > 0 ? formatCurrency(totalInterestPaid) : null);
        kpi.put("overdueContractsCount", overdueCount > 0 ? (overdueCount + " HĐ") : null);
        kpi.put("pendingApprovalCount", pendingCount);
        kpi.put("disbursedBalance", totalDisbursed);
        kpi.put("expectedInterestAmount", totalInterestPaid);
        kpi.put("overdueCount", overdueCount);
        kpi.put("overdueAmount", overdueAmount);

        return kpi;
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

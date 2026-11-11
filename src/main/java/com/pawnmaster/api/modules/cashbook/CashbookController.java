package com.pawnmaster.api.modules.cashbook;

import com.pawnmaster.api.common.BaseResponse;
import com.pawnmaster.api.modules.cashbook.dto.CashFlowSummaryResponse;
import com.pawnmaster.api.modules.cashbook.dto.CashbookCreateRequest;
import com.pawnmaster.api.modules.cashbook.dto.CashbookResponse;
import com.pawnmaster.api.security.BranchSecurityGuard;
import com.pawnmaster.api.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/cashbook", "/api/v1/cashbooks"})
@RequiredArgsConstructor
public class CashbookController {

    private final CashbookService cashbookService;

    @Autowired(required = false)
    private BranchSecurityGuard branchSecurityGuard = new BranchSecurityGuard();

    @GetMapping("/health")
    public BaseResponse<String> healthCheck() {
        return BaseResponse.ok("Cashbook module is ready");
    }

    @PostMapping({"/transactions", ""})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF', 'ACCOUNTANT')")
    public BaseResponse<CashbookResponse> recordTransaction(
            @Valid @RequestBody CashbookCreateRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        if (branchSecurityGuard != null) {
            branchSecurityGuard.validateBranchAccess(userPrincipal, request.getBranchId());
        }
        if (request.getPerformedBy() == null && userPrincipal != null) {
            request.setPerformedBy(userPrincipal.getId());
        }
        CashbookResponse response = cashbookService.recordTransaction(request);
        return BaseResponse.ok("Ghi nhận phiếu thu/chi thành công", response);
    }

    @GetMapping({"/transactions", ""})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF', 'ACCOUNTANT')")
    public BaseResponse<List<CashbookResponse>> getTransactions(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) TransactionType type
    ) {
        Long effectiveBranchId = branchId;
        UserPrincipal currentUser = branchSecurityGuard != null ? branchSecurityGuard.getCurrentUserOrNull() : null;
        if (branchSecurityGuard != null && currentUser != null) {
            if (branchId != null) {
                branchSecurityGuard.validateBranchAccess(currentUser, branchId);
            } else if (!branchSecurityGuard.isAdmin(currentUser)) {
                effectiveBranchId = currentUser.getBranchId();
            }
        }

        List<CashbookResponse> list;
        if (effectiveBranchId != null && type != null) {
            list = cashbookService.getTransactionsByBranchAndType(effectiveBranchId, type);
        } else if (effectiveBranchId != null) {
            list = cashbookService.getTransactionsByBranch(effectiveBranchId);
        } else {
            list = cashbookService.getAllTransactions();
        }
        return BaseResponse.ok(list);
    }

    @GetMapping("/transactions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF', 'ACCOUNTANT')")
    public BaseResponse<CashbookResponse> getTransactionById(
            @PathVariable Long id
    ) {
        CashbookResponse tx = cashbookService.getTransactionById(id);
        UserPrincipal currentUser = branchSecurityGuard != null ? branchSecurityGuard.getCurrentUserOrNull() : null;
        if (branchSecurityGuard != null && currentUser != null && tx != null && tx.getBranchId() != null) {
            branchSecurityGuard.validateBranchAccess(currentUser, tx.getBranchId());
        }
        return BaseResponse.ok(tx);
    }

    @GetMapping({"/summary", "/branches/{branchId}/summary"})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF', 'ACCOUNTANT')")
    public BaseResponse<CashFlowSummaryResponse> getBranchSummary(
            @PathVariable(required = false) Long branchId,
            @RequestParam(required = false) Long branchIdParam
    ) {
        Long targetBranchId = branchId != null ? branchId : branchIdParam;
        UserPrincipal currentUser = branchSecurityGuard != null ? branchSecurityGuard.getCurrentUserOrNull() : null;
        if (branchSecurityGuard != null && currentUser != null) {
            if (targetBranchId != null) {
                branchSecurityGuard.validateBranchAccess(currentUser, targetBranchId);
            } else if (!branchSecurityGuard.isAdmin(currentUser)) {
                targetBranchId = currentUser.getBranchId();
            }
        }
        if (targetBranchId == null) {
            if (currentUser != null && branchSecurityGuard != null && branchSecurityGuard.isAdmin(currentUser)) {
                return BaseResponse.ok(cashbookService.getBranchCashFlowSummary(null));
            }
            return BaseResponse.ok(new CashFlowSummaryResponse());
        }
        return BaseResponse.ok(cashbookService.getBranchCashFlowSummary(targetBranchId));
    }

    @GetMapping("/contracts/{contractId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF', 'ACCOUNTANT')")
    public BaseResponse<List<CashbookResponse>> getTransactionsByContract(@PathVariable Long contractId) {
        return BaseResponse.ok(cashbookService.getTransactionsByContract(contractId));
    }
}

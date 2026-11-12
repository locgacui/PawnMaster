package com.pawnmaster.api.modules.contract;

import com.pawnmaster.api.common.BaseResponse;
import com.pawnmaster.api.modules.contract.dto.ContractApprovalRequest;
import com.pawnmaster.api.modules.contract.dto.ContractCreateRequest;
import com.pawnmaster.api.modules.contract.dto.ContractRedeemRequest;
import com.pawnmaster.api.modules.contract.dto.ContractRejectRequest;
import com.pawnmaster.api.modules.contract.dto.ContractResponse;
import com.pawnmaster.api.modules.contract.dto.InterestCalculationResponse;
import com.pawnmaster.api.modules.contract.dto.InterestCollectionRequest;
import com.pawnmaster.api.modules.contract.dto.InterestPaymentResponse;
import com.pawnmaster.api.modules.contract.dto.LiquidationRequest;
import com.pawnmaster.api.modules.contract.dto.QuickContractOriginationRequest;
import com.pawnmaster.api.security.BranchSecurityGuard;
import com.pawnmaster.api.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    @Autowired(required = false)
    private BranchSecurityGuard branchSecurityGuard = new BranchSecurityGuard();

    @GetMapping("/health")
    public BaseResponse<String> healthCheck() {
        return BaseResponse.ok("Contract module is ready");
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public BaseResponse<ContractResponse> createContract(
            @Valid @RequestBody ContractCreateRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        if (branchSecurityGuard != null && request.getBranchId() != null) {
            branchSecurityGuard.validateBranchAccess(userPrincipal, request.getBranchId());
        }
        Long staffId = userPrincipal != null ? userPrincipal.getId() : 1L;
        ContractResponse response = contractService.createContract(request, staffId);
        return BaseResponse.ok("Tạo hồ sơ hợp đồng thành công (chờ duyệt)", response);
    }

    @PostMapping("/originate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public BaseResponse<ContractResponse> originateFullContract(
            @Valid @RequestBody QuickContractOriginationRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        if (branchSecurityGuard != null) {
            branchSecurityGuard.validateBranchAccess(userPrincipal, request.getBranchId());
        }
        Long staffId = userPrincipal != null ? userPrincipal.getId() : 1L;
        ContractResponse response = contractService.originateFullContract(request, staffId);
        return BaseResponse.ok("Khởi tạo hợp đồng cầm đồ mới thành công (Trạng thái: Chờ duyệt)", response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF', 'ACCOUNTANT')")
    public BaseResponse<ContractResponse> getContractById(@PathVariable Long id) {
        return BaseResponse.ok(contractService.getContractById(id));
    }

    @GetMapping("/code/{contractCode}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF', 'ACCOUNTANT')")
    public BaseResponse<ContractResponse> getContractByCode(@PathVariable String contractCode) {
        return BaseResponse.ok(contractService.getContractByCode(contractCode));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF', 'ACCOUNTANT')")
    public BaseResponse<List<ContractResponse>> getContracts(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(required = false) Long customerId
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

        List<ContractResponse> result;
        if (customerId != null) {
            result = contractService.getContractsByCustomer(customerId);
        } else if (effectiveBranchId != null && status != null) {
            result = contractService.getContractsByBranchAndStatus(effectiveBranchId, status);
        } else if (effectiveBranchId != null) {
            result = contractService.getContractsByBranch(effectiveBranchId);
        } else if (status != null) {
            result = contractService.getContractsByStatus(status);
        } else {
            result = contractService.getAllContracts();
        }
        return BaseResponse.ok(result);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public BaseResponse<ContractResponse> approveContract(
            @PathVariable Long id,
            @RequestBody(required = false) ContractApprovalRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        Long approverId = userPrincipal != null ? userPrincipal.getId() : 1L;
        String notes = request != null && request.getApprovalNotes() != null
                ? request.getApprovalNotes()
                : "Phê duyệt giải ngân hợp đồng";
        ContractResponse response = contractService.approveAndDisburseContract(id, approverId, notes);
        return BaseResponse.ok("Phê duyệt và giải ngân hợp đồng thành công", response);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public BaseResponse<ContractResponse> rejectContract(
            @PathVariable Long id,
            @Valid @RequestBody ContractRejectRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        Long approverId = userPrincipal != null ? userPrincipal.getId() : 1L;
        ContractResponse response = contractService.rejectContract(id, approverId, request.getRejectionReason());
        return BaseResponse.ok("Từ chối duyệt hợp đồng thành công", response);
    }

    @PostMapping({"/{id}/calculate-interest", "/{id}/calculate"})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF', 'ACCOUNTANT')")
    public BaseResponse<InterestCalculationResponse> calculateInterest(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        return BaseResponse.ok(contractService.calculateContractInterest(id, fromDate, toDate));
    }

    @PostMapping({"/{id}/interest", "/{id}/collect-interest"})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public BaseResponse<InterestPaymentResponse> collectInterest(
            @PathVariable Long id,
            @Valid @RequestBody InterestCollectionRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        Long staffId = userPrincipal != null ? userPrincipal.getId() : 1L;
        InterestPaymentResponse response = contractService.collectInterest(id, request, staffId);
        return BaseResponse.ok("Thu lãi/gốc định kỳ thành công", response);
    }

    @PostMapping("/{id}/redeem")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public BaseResponse<ContractResponse> redeemContract(
            @PathVariable Long id,
            @RequestBody(required = false) ContractRedeemRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        Long staffId = userPrincipal != null ? userPrincipal.getId() : 1L;
        if (request != null && request.getBranchId() != null) {
            if (request.getNotes() == null) {
                request.setNotes("Khách tất toán chuộc tài sản");
            }
            ContractResponse response = contractService.redeemContract(id, request, staffId);
            return BaseResponse.ok("Tất toán chuộc tài sản thành công", response);
        }
        String notes = request != null && request.getNotes() != null
                ? request.getNotes()
                : "Khách tất toán chuộc tài sản";
        ContractResponse response = contractService.redeemContract(id, staffId, notes);
        return BaseResponse.ok("Tất toán chuộc tài sản thành công", response);
    }

    @PostMapping("/{id}/liquidate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public BaseResponse<ContractResponse> liquidateContract(
            @PathVariable Long id,
            @Valid @RequestBody LiquidationRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        Long managerId = userPrincipal != null ? userPrincipal.getId() : 1L;
        ContractResponse response = contractService.liquidateContract(id, request, managerId);
        return BaseResponse.ok("Thanh lý tài sản hợp đồng thành công", response);
    }

    @PostMapping("/mark-overdue")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public BaseResponse<Integer> markOverdueContracts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        int count = asOfDate != null
                ? contractService.markOverdueContracts(asOfDate)
                : contractService.markOverdueContracts();
        return BaseResponse.ok("Đã quét và cập nhật " + count + " hợp đồng quá hạn", count);
    }

    @GetMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF', 'ACCOUNTANT')")
    public BaseResponse<List<InterestPaymentResponse>> getContractPayments(@PathVariable Long id) {
        return BaseResponse.ok(contractService.getContractPayments(id));
    }
}

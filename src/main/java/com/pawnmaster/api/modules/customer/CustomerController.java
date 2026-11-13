package com.pawnmaster.api.modules.customer;

import com.pawnmaster.api.common.BaseResponse;
import com.pawnmaster.api.modules.customer.dto.CustomerCreateRequest;
import com.pawnmaster.api.modules.customer.dto.CustomerResponse;
import com.pawnmaster.api.modules.customer.dto.CustomerUpdateRequest;
import com.pawnmaster.api.modules.customer.dto.OccupationDocumentPolicyItemDto;
import com.pawnmaster.api.modules.customer.dto.UpsertOccupationDocPolicyRequest;
import com.pawnmaster.api.modules.customer.dto.UpsertVerificationDocTypeRequest;
import com.pawnmaster.api.modules.customer.dto.VerificationDocumentMatrixResponse;
import com.pawnmaster.api.modules.customer.dto.VerificationDocumentTypeDto;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;
    private VerificationDocumentPolicy verificationDocumentPolicy = new VerificationDocumentPolicy();

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Autowired(required = false)
    void setVerificationDocumentPolicy(VerificationDocumentPolicy verificationDocumentPolicy) {
        if (verificationDocumentPolicy != null) {
            this.verificationDocumentPolicy = verificationDocumentPolicy;
        }
    }

    @GetMapping("/health")
    public BaseResponse<String> healthCheck() {
        return BaseResponse.ok("Customer module is ready");
    }

    @PostMapping
    public BaseResponse<CustomerResponse> createCustomer(@Valid @RequestBody CustomerCreateRequest request) {
        return BaseResponse.ok("Tạo khách hàng thành công", customerService.createCustomer(request));
    }

    @GetMapping
    public BaseResponse<List<CustomerResponse>> getAllCustomers(@RequestParam(name = "branchId", required = false) Long branchId) {
        if (branchId != null) {
            return BaseResponse.ok(customerService.getCustomersByBranch(branchId));
        }
        return BaseResponse.ok(customerService.getAllCustomers());
    }

    @GetMapping("/{id}")
    public BaseResponse<CustomerResponse> getCustomerById(@PathVariable Long id) {
        return BaseResponse.ok(customerService.getCustomerById(id));
    }

    @GetMapping("/code/{customerCode}")
    public BaseResponse<CustomerResponse> getCustomerByCode(@PathVariable String customerCode) {
        return BaseResponse.ok(customerService.getCustomerByCode(customerCode));
    }

    @GetMapping("/identity/{identityNumber}")
    public BaseResponse<CustomerResponse> getCustomerByIdentityNumber(@PathVariable String identityNumber) {
        return BaseResponse.ok(customerService.getCustomerByIdentityNumber(identityNumber));
    }

    @PutMapping("/{id}")
    public BaseResponse<CustomerResponse> updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody CustomerUpdateRequest request
    ) {
        return BaseResponse.ok("Cập nhật khách hàng thành công", customerService.updateCustomer(id, request));
    }

    @DeleteMapping("/{id}")
    public BaseResponse<Void> deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return BaseResponse.ok("Xóa khách hàng thành công", null);
    }

    @GetMapping("/occupation-groups")
    public BaseResponse<List<Map<String, String>>> getOccupationGroups() {
        List<Map<String, String>> list = Arrays.stream(CustomerOccupationGroup.values())
                .map(g -> Map.of("code", g.name(), "name", g.getDescription()))
                .toList();
        return BaseResponse.ok(list);
    }

    @GetMapping("/verification-doc-types")
    public BaseResponse<List<VerificationDocumentTypeDto>> getVerificationDocTypes(
            @RequestParam(name = "occupationGroup", required = false) CustomerOccupationGroup occupationGroup,
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        return BaseResponse.ok(verificationDocumentPolicy.getDocumentTypes(occupationGroup, includeInactive));
    }

    @GetMapping("/verification-doc-policies")
    public BaseResponse<VerificationDocumentMatrixResponse> getVerificationDocPolicies(
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        return BaseResponse.ok(verificationDocumentPolicy.getPolicyMatrix(includeInactive));
    }

    @PostMapping("/verification-doc-types")
    @PreAuthorize("hasRole('ADMIN')")
    public BaseResponse<VerificationDocumentTypeDto> upsertVerificationDocType(
            @Valid @RequestBody UpsertVerificationDocTypeRequest request) {
        return BaseResponse.ok("Lưu loại chứng từ thành công",
                verificationDocumentPolicy.upsertDocumentType(request));
    }

    @PutMapping("/verification-doc-policies")
    @PreAuthorize("hasRole('ADMIN')")
    public BaseResponse<OccupationDocumentPolicyItemDto> upsertVerificationDocPolicy(
            @Valid @RequestBody UpsertOccupationDocPolicyRequest request) {
        return BaseResponse.ok("Cập nhật cấu hình chứng từ theo nghề nghiệp thành công",
                verificationDocumentPolicy.upsertOccupationDocumentPolicy(request));
    }
}

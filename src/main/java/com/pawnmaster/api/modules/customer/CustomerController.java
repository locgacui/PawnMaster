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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;
    private VerificationDocumentPolicy verificationDocumentPolicy;

    @Autowired
    public CustomerController(
            CustomerService customerService,
            @Autowired(required = false) VerificationDocumentPolicy verificationDocumentPolicy) {
        this.customerService = customerService;
        this.verificationDocumentPolicy = verificationDocumentPolicy;
    }

    public CustomerController(CustomerService customerService) {
        this(customerService, null);
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

    private VerificationDocumentPolicy requireVerificationDocumentPolicy() {
        if (verificationDocumentPolicy == null) {
            throw new com.pawnmaster.api.common.BusinessException(
                    com.pawnmaster.api.common.ErrorCode.INVALID_REQUEST,
                    "Dịch vụ cấu hình chứng từ chưa kết nối CSDL");
        }
        return verificationDocumentPolicy;
    }

    @GetMapping("/verification-doc-types")
    public BaseResponse<List<VerificationDocumentTypeDto>> getVerificationDocTypes(
            @RequestParam(name = "occupationGroup", required = false) CustomerOccupationGroup occupationGroup,
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        if (verificationDocumentPolicy == null) {
            return BaseResponse.ok(Collections.emptyList());
        }
        return BaseResponse.ok(verificationDocumentPolicy.getDocumentTypes(occupationGroup, includeInactive));
    }

    @GetMapping("/verification-doc-policies")
    public BaseResponse<VerificationDocumentMatrixResponse> getVerificationDocPolicies(
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        if (verificationDocumentPolicy == null) {
            Map<String, List<OccupationDocumentPolicyItemDto>> emptyMatrix = new LinkedHashMap<>();
            for (CustomerOccupationGroup group : CustomerOccupationGroup.values()) {
                emptyMatrix.put(group.name(), Collections.emptyList());
            }
            return BaseResponse.ok(VerificationDocumentMatrixResponse.builder()
                    .documentTypes(Collections.emptyList())
                    .policiesByOccupation(emptyMatrix)
                    .build());
        }
        return BaseResponse.ok(verificationDocumentPolicy.getPolicyMatrix(includeInactive));
    }

    @PostMapping("/verification-doc-types")
    @PreAuthorize("hasRole('ADMIN')")
    public BaseResponse<VerificationDocumentTypeDto> upsertVerificationDocType(
            @Valid @RequestBody UpsertVerificationDocTypeRequest request) {
        return BaseResponse.ok("Lưu loại chứng từ thành công",
                requireVerificationDocumentPolicy().upsertDocumentType(request));
    }

    @PutMapping("/verification-doc-policies")
    @PreAuthorize("hasRole('ADMIN')")
    public BaseResponse<OccupationDocumentPolicyItemDto> upsertVerificationDocPolicy(
            @Valid @RequestBody UpsertOccupationDocPolicyRequest request) {
        return BaseResponse.ok("Cập nhật cấu hình chứng từ theo nghề nghiệp thành công",
                requireVerificationDocumentPolicy().upsertOccupationDocumentPolicy(request));
    }
}

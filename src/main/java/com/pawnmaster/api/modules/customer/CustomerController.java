package com.pawnmaster.api.modules.customer;

import com.pawnmaster.api.common.BaseResponse;
import com.pawnmaster.api.modules.customer.dto.CustomerCreateRequest;
import com.pawnmaster.api.modules.customer.dto.CustomerResponse;
import com.pawnmaster.api.modules.customer.dto.CustomerUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

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
    public BaseResponse<List<Map<String, String>>> getVerificationDocTypes() {
        List<Map<String, String>> list = Arrays.stream(VerificationDocumentType.values())
                .map(d -> Map.of("code", d.name(), "name", d.getDescription()))
                .toList();
        return BaseResponse.ok(list);
    }
}

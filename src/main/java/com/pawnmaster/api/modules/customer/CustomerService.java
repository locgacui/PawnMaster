package com.pawnmaster.api.modules.customer;

import com.pawnmaster.api.modules.customer.dto.CustomerCreateRequest;
import com.pawnmaster.api.modules.customer.dto.CustomerResponse;
import com.pawnmaster.api.modules.customer.dto.CustomerUpdateRequest;

import java.util.List;

public interface CustomerService {

    CustomerResponse createCustomer(CustomerCreateRequest request);

    CustomerResponse getCustomerById(Long id);

    CustomerResponse getCustomerByCode(String customerCode);

    CustomerResponse getCustomerByIdentityNumber(String identityNumber);

    List<CustomerResponse> getAllCustomers();

    List<CustomerResponse> getCustomersByBranch(Long branchId);

    CustomerResponse updateCustomer(Long id, CustomerUpdateRequest request);

    void deleteCustomer(Long id);
}

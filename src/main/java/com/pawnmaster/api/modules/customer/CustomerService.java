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

    boolean isDocumentValidForGroup(CustomerOccupationGroup group, String docCode);

    default boolean isDocumentCodeValidForGroup(CustomerOccupationGroup group, String docCode) {
        return isDocumentValidForGroup(group, docCode);
    }

    List<String> getAllowedDocumentCodes(CustomerOccupationGroup group);

    void validateVerificationDocuments(CustomerOccupationGroup group, String docCode, java.util.Collection<String> docUrls);

    default void validateVerificationDocumentsByCode(CustomerOccupationGroup group, String docCode, java.util.Collection<String> docUrls) {
        validateVerificationDocuments(group, docCode, docUrls);
    }
}

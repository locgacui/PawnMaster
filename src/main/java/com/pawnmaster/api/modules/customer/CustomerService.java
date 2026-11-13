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

    boolean isDocumentValidForGroup(CustomerOccupationGroup group, VerificationDocumentType docType);

    default boolean isDocumentCodeValidForGroup(CustomerOccupationGroup group, String docCode) {
        VerificationDocumentType parsed = VerificationDocumentType.fromCodeOrNull(docCode);
        return parsed != null && isDocumentValidForGroup(group, parsed);
    }

    java.util.Set<VerificationDocumentType> getAllowedDocuments(CustomerOccupationGroup group);

    default java.util.List<String> getAllowedDocumentCodes(CustomerOccupationGroup group) {
        return getAllowedDocuments(group).stream().map(Enum::name).toList();
    }

    void validateVerificationDocuments(CustomerOccupationGroup group, VerificationDocumentType docType, java.util.Collection<String> docUrls);

    default void validateVerificationDocumentsByCode(CustomerOccupationGroup group, String docCode, java.util.Collection<String> docUrls) {
        VerificationDocumentType parsed = VerificationDocumentType.fromCodeOrNull(docCode);
        validateVerificationDocuments(group, parsed, docUrls);
    }
}

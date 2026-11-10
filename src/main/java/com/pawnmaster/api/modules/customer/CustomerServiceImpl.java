package com.pawnmaster.api.modules.customer;

import com.pawnmaster.api.common.BusinessCodeGenerator;
import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.branch.BranchEntity;
import com.pawnmaster.api.modules.branch.BranchRepository;
import com.pawnmaster.api.modules.customer.dto.CustomerCreateRequest;
import com.pawnmaster.api.modules.customer.dto.CustomerResponse;
import com.pawnmaster.api.modules.customer.dto.CustomerUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final BranchRepository branchRepository;
    private final VerificationDocumentPolicy verificationDocumentPolicy;

    @Override
    @Transactional
    public CustomerResponse createCustomer(CustomerCreateRequest request) {
        if (customerRepository.existsByIdentityNumber(request.getIdentityNumber())) {
            throw new BusinessException(ErrorCode.CUSTOMER_IDENTITY_EXISTS);
        }

        CustomerOccupationGroup occupation = request.getOccupationGroup() != null
                ? request.getOccupationGroup()
                : CustomerOccupationGroup.FREELANCER;

        verificationDocumentPolicy.validateVerificationDocuments(
                occupation,
                request.getVerificationDocType(),
                request.getVerificationDocUrls());

        BranchEntity branch = null;
        if (request.getBranchId() != null) {
            branch = branchRepository.findById(request.getBranchId()).orElse(null);
        }

        String customerCode = request.getCustomerCode();
        if (customerCode == null || customerCode.isBlank()) {
            customerCode = BusinessCodeGenerator.generateCustomerCode();
        }

        String docUrlsStr = String.join(",", request.getVerificationDocUrls());

        CustomerEntity customer = CustomerEntity.builder()
                .customerCode(customerCode)
                .branch(branch)
                .fullName(request.getFullName())
                .identityNumber(request.getIdentityNumber())
                .identityIssueDate(request.getIdentityIssueDate())
                .identityIssuePlace(request.getIdentityIssuePlace())
                .phoneNumber(request.getPhoneNumber())
                .email(request.getEmail())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .provinceCode(request.getProvinceCode())
                .districtCode(request.getDistrictCode())
                .address(request.getAddress())
                .permanentAddress(request.getPermanentAddress())
                .occupationGroup(occupation)
                .monthlyIncome(request.getMonthlyIncome())
                .hasSocialInsurance(request.isHasSocialInsurance())
                .verificationDocType(request.getVerificationDocType())
                .verificationDocUrls(docUrlsStr)
                .idCardFrontUrl(request.getIdCardFrontUrl())
                .idCardBackUrl(request.getIdCardBackUrl())
                .portraitUrl(request.getPortraitUrl())
                .notes(request.getNotes())
                .status(CustomerStatus.ACTIVE)
                .build();

        CustomerEntity saved = customerRepository.save(customer);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(Long id) {
        CustomerEntity customer = customerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));
        return mapToResponse(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerByCode(String customerCode) {
        CustomerEntity customer = customerRepository.findByCustomerCodeAndDeletedFalse(customerCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));
        return mapToResponse(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerByIdentityNumber(String identityNumber) {
        CustomerEntity customer = customerRepository.findByIdentityNumberAndDeletedFalse(identityNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));
        return mapToResponse(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> getAllCustomers() {
        return customerRepository.findAllByDeletedFalse().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerResponse> getCustomersByBranch(Long branchId) {
        return customerRepository.findByBranchIdAndDeletedFalse(branchId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public CustomerResponse updateCustomer(Long id, CustomerUpdateRequest request) {
        CustomerEntity customer = customerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));

        if (request.getFullName() != null)
            customer.setFullName(request.getFullName());
        if (request.getPhoneNumber() != null)
            customer.setPhoneNumber(request.getPhoneNumber());
        if (request.getEmail() != null)
            customer.setEmail(request.getEmail());
        if (request.getAddress() != null)
            customer.setAddress(request.getAddress());
        if (request.getPermanentAddress() != null)
            customer.setPermanentAddress(request.getPermanentAddress());
        if (request.getOccupationGroup() != null)
            customer.setOccupationGroup(request.getOccupationGroup());
        if (request.getMonthlyIncome() != null)
            customer.setMonthlyIncome(request.getMonthlyIncome());
        if (request.getHasSocialInsurance() != null)
            customer.setHasSocialInsurance(request.getHasSocialInsurance());
        if (request.getVerificationDocType() != null)
            customer.setVerificationDocType(request.getVerificationDocType());
        if (request.getVerificationDocUrls() != null) {
            customer.setVerificationDocUrls(String.join(",", request.getVerificationDocUrls()));
        }
        if (request.getStatus() != null)
            customer.setStatus(request.getStatus());
        if (request.getNotes() != null)
            customer.setNotes(request.getNotes());

        return mapToResponse(customer);
    }

    @Override
    @Transactional
    public void deleteCustomer(Long id) {
        CustomerEntity customer = customerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));
        customer.setDeleted(true);
    }

    @Override
    public boolean isDocumentValidForGroup(CustomerOccupationGroup group, VerificationDocumentType docType) {
        return verificationDocumentPolicy.isDocumentValidForGroup(group, docType);
    }

    @Override
    public Set<VerificationDocumentType> getAllowedDocuments(CustomerOccupationGroup group) {
        return verificationDocumentPolicy.getAllowedDocuments(group);
    }

    @Override
    public void validateVerificationDocuments(CustomerOccupationGroup group, VerificationDocumentType docType,
            Collection<String> docUrls) {
        verificationDocumentPolicy.validateVerificationDocuments(group, docType, docUrls);
    }

    private CustomerResponse mapToResponse(CustomerEntity entity) {
        return CustomerResponse.builder()
                .id(entity.getId())
                .customerCode(entity.getCustomerCode())
                .branchId(entity.getBranchId())
                .branchCode(entity.getBranchCode())
                .branchName(entity.getBranchName())
                .fullName(entity.getFullName())
                .identityNumber(entity.getIdentityNumber())
                .identityIssueDate(entity.getIdentityIssueDate())
                .identityIssuePlace(entity.getIdentityIssuePlace())
                .phoneNumber(entity.getPhoneNumber())
                .email(entity.getEmail())
                .dateOfBirth(entity.getDateOfBirth())
                .gender(entity.getGender())
                .provinceCode(entity.getProvinceCode())
                .districtCode(entity.getDistrictCode())
                .address(entity.getAddress())
                .permanentAddress(entity.getPermanentAddress())
                .occupationGroup(entity.getOccupationGroup())
                .occupationGroupDescription(
                        entity.getOccupationGroup() != null ? entity.getOccupationGroup().getDescription() : null)
                .monthlyIncome(entity.getMonthlyIncome())
                .hasSocialInsurance(entity.isHasSocialInsurance())
                .verificationDocType(entity.getVerificationDocType())
                .verificationDocTypeDescription(
                        entity.getVerificationDocType() != null ? entity.getVerificationDocType().getDescription()
                                : null)
                .verificationDocUrls(entity.getVerificationDocUrls())
                .verificationDocUrlList(entity.getVerificationDocUrlList())
                .idCardFrontUrl(entity.getIdCardFrontUrl())
                .idCardBackUrl(entity.getIdCardBackUrl())
                .portraitUrl(entity.getPortraitUrl())
                .status(entity.getStatus())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

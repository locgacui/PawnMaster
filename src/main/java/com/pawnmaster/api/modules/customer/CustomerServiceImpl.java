package com.pawnmaster.api.modules.customer;

import com.pawnmaster.api.common.BusinessCodeGenerator;
import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.branch.BranchRepository;
import com.pawnmaster.api.modules.branch.DistrictEntity;
import com.pawnmaster.api.modules.branch.DistrictRepository;
import com.pawnmaster.api.modules.branch.ProvinceEntity;
import com.pawnmaster.api.modules.branch.ProvinceRepository;
import com.pawnmaster.api.modules.branch.WardEntity;
import com.pawnmaster.api.modules.branch.WardRepository;
import com.pawnmaster.api.modules.customer.dto.CustomerCreateRequest;
import com.pawnmaster.api.modules.customer.dto.CustomerResponse;
import com.pawnmaster.api.modules.customer.dto.CustomerUpdateRequest;
import com.pawnmaster.api.modules.storage.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final VerificationDocumentPolicy verificationDocumentPolicy;
    private ProvinceRepository provinceRepository;
    private DistrictRepository districtRepository;
    private WardRepository wardRepository;
    private FileStorageService fileStorageService;

    @Autowired
    public CustomerServiceImpl(
            CustomerRepository customerRepository,
            @Autowired(required = false) BranchRepository branchRepository,
            VerificationDocumentPolicy verificationDocumentPolicy,
            @Autowired(required = false) ProvinceRepository provinceRepository,
            @Autowired(required = false) DistrictRepository districtRepository,
            @Autowired(required = false) WardRepository wardRepository,
            @Autowired(required = false) FileStorageService fileStorageService
    ) {
        this.customerRepository = customerRepository;
        this.verificationDocumentPolicy = verificationDocumentPolicy != null
                ? verificationDocumentPolicy
                : new VerificationDocumentPolicy();
        this.provinceRepository = provinceRepository;
        this.districtRepository = districtRepository;
        this.wardRepository = wardRepository;
        this.fileStorageService = fileStorageService;
    }

    public CustomerServiceImpl(
            CustomerRepository customerRepository,
            BranchRepository branchRepository,
            VerificationDocumentPolicy verificationDocumentPolicy,
            ProvinceRepository provinceRepository,
            DistrictRepository districtRepository,
            WardRepository wardRepository
    ) {
        this(customerRepository, branchRepository, verificationDocumentPolicy, provinceRepository, districtRepository, wardRepository, null);
    }

    public CustomerServiceImpl(
            CustomerRepository customerRepository,
            BranchRepository branchRepository,
            VerificationDocumentPolicy verificationDocumentPolicy
    ) {
        this(customerRepository, branchRepository, verificationDocumentPolicy, null, null, null, null);
    }

    public CustomerServiceImpl(
            CustomerRepository customerRepository,
            VerificationDocumentPolicy verificationDocumentPolicy
    ) {
        this(customerRepository, null, verificationDocumentPolicy, null, null, null, null);
    }

    public void setFileStorageService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public void setLocationRepositories(
            ProvinceRepository provinceRepository,
            DistrictRepository districtRepository,
            WardRepository wardRepository
    ) {
        this.provinceRepository = provinceRepository;
        this.districtRepository = districtRepository;
        this.wardRepository = wardRepository;
    }

    public record ResolvedCustomerLocation(
            String provinceCode,
            ProvinceEntity province,
            String provinceName,
            String districtCode,
            DistrictEntity district,
            String districtName,
            String wardCode,
            WardEntity ward,
            String wardName,
            String addressDetail,
            String permanentAddress,
            String address
    ) {
    }

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

        ResolvedCustomerLocation loc = resolveAndComposeLocation(
                request.getProvinceCode(),
                request.getDistrictCode(),
                request.getWardCode(),
                request.getAddressDetail(),
                request.getPermanentAddress(),
                request.getAddress(),
                provinceRepository,
                districtRepository,
                wardRepository
        );

        String customerCode = request.getCustomerCode();
        if (customerCode == null || customerCode.isBlank()) {
            customerCode = BusinessCodeGenerator.generateCustomerCode();
        }

        String docUrlsStr = String.join(",", request.getVerificationDocUrls());

        CustomerEntity customer = CustomerEntity.builder()
                .customerCode(customerCode)
                .fullName(request.getFullName())
                .identityNumber(request.getIdentityNumber())
                .identityIssueDate(request.getIdentityIssueDate())
                .identityIssuePlace(request.getIdentityIssuePlace())
                .phoneNumber(request.getPhoneNumber())
                .email(request.getEmail())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .provinceCode(loc.provinceCode())
                .province(loc.province())
                .districtCode(loc.districtCode())
                .district(loc.district())
                .wardCode(loc.wardCode())
                .ward(loc.ward())
                .address(loc.address())
                .permanentAddress(loc.permanentAddress())
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
        return mapToResponse(saved != null ? saved : customer);
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
        if (request.getIdentityIssueDate() != null)
            customer.setIdentityIssueDate(request.getIdentityIssueDate());
        if (request.getIdentityIssuePlace() != null)
            customer.setIdentityIssuePlace(request.getIdentityIssuePlace());
        if (request.getPhoneNumber() != null)
            customer.setPhoneNumber(request.getPhoneNumber());
        if (request.getEmail() != null)
            customer.setEmail(request.getEmail());
        if (request.getDateOfBirth() != null)
            customer.setDateOfBirth(request.getDateOfBirth());
        if (request.getGender() != null)
            customer.setGender(request.getGender());

        boolean hasLocationOrAddressUpdate = request.getProvinceCode() != null
                || request.getDistrictCode() != null
                || request.getWardCode() != null
                || request.getAddressDetail() != null
                || request.getPermanentAddress() != null
                || request.getAddress() != null;

        if (hasLocationOrAddressUpdate) {
            String effProvinceCode = request.getProvinceCode() != null
                    ? cleanCode(request.getProvinceCode())
                    : customer.getProvinceCode();
            String effDistrictCode = request.getDistrictCode() != null
                    ? cleanCode(request.getDistrictCode())
                    : customer.getDistrictCode();
            String effWardCode = request.getWardCode() != null
                    ? cleanCode(request.getWardCode())
                    : customer.getWardCode();

            String seedDetail = request.getAddressDetail();
            if (seedDetail == null && request.getPermanentAddress() == null && request.getAddress() == null) {
                seedDetail = extractExistingCustomerStreet(customer);
            }

            ResolvedCustomerLocation loc = resolveAndComposeLocation(
                    effProvinceCode,
                    effDistrictCode,
                    effWardCode,
                    seedDetail,
                    request.getPermanentAddress(),
                    request.getAddress(),
                    provinceRepository,
                    districtRepository,
                    wardRepository
            );

            customer.setProvinceCode(loc.provinceCode());
            customer.setProvince(loc.province());
            customer.setDistrictCode(loc.districtCode());
            customer.setDistrict(loc.district());
            customer.setWardCode(loc.wardCode());
            customer.setWard(loc.ward());
            if (loc.permanentAddress() != null) {
                customer.setPermanentAddress(loc.permanentAddress());
            }
            if (loc.address() != null) {
                customer.setAddress(loc.address());
            }
        }

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
        if (request.getIdCardFrontUrl() != null)
            customer.setIdCardFrontUrl(request.getIdCardFrontUrl());
        if (request.getIdCardBackUrl() != null)
            customer.setIdCardBackUrl(request.getIdCardBackUrl());
        if (request.getPortraitUrl() != null)
            customer.setPortraitUrl(request.getPortraitUrl());
        if (request.getStatus() != null)
            customer.setStatus(request.getStatus());
        if (request.getNotes() != null)
            customer.setNotes(request.getNotes());

        CustomerEntity saved = customerRepository.save(customer);
        return mapToResponse(saved != null ? saved : customer);
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

    public static ResolvedCustomerLocation resolveAndComposeLocation(
            String rawProvinceCode,
            String rawDistrictCode,
            String rawWardCode,
            String rawAddressDetail,
            String rawPermanentAddress,
            String rawContactAddress,
            ProvinceRepository provinceRepository,
            DistrictRepository districtRepository,
            WardRepository wardRepository
    ) {
        String provinceCode = cleanCode(rawProvinceCode);
        String districtCode = cleanCode(rawDistrictCode);
        String wardCode = cleanCode(rawWardCode);

        ProvinceEntity province = null;
        if (provinceCode != null && provinceRepository != null) {
            Optional<ProvinceEntity> pOpt = provinceRepository.findById(provinceCode);
            if (pOpt.isPresent()) {
                province = pOpt.get();
            } else if (provinceRepository.count() > 0) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Không tìm thấy Tỉnh/Thành phố với mã: " + provinceCode
                );
            }
        }

        DistrictEntity district = null;
        if (districtCode != null && districtRepository != null) {
            Optional<DistrictEntity> dOpt = districtRepository.findById(districtCode);
            if (dOpt.isPresent()) {
                district = dOpt.get();
                if (provinceCode != null
                        && district.getProvinceCode() != null
                        && !district.getProvinceCode().equals(provinceCode)) {
                    throw new BusinessException(
                            ErrorCode.INVALID_REQUEST,
                            String.format("Quận/Huyện [%s] không thuộc Tỉnh/Thành phố [%s]", districtCode, provinceCode)
                    );
                }
                if (provinceCode == null && district.getProvinceCode() != null && !district.getProvinceCode().isBlank()) {
                    provinceCode = district.getProvinceCode();
                    if (provinceRepository != null) {
                        province = provinceRepository.findById(provinceCode).orElse(null);
                    }
                }
            } else if (districtRepository.count() > 0) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Không tìm thấy Quận/Huyện với mã: " + districtCode
                );
            }
        }

        WardEntity ward = null;
        if (wardCode != null && wardRepository != null) {
            Optional<WardEntity> wOpt = wardRepository.findById(wardCode);
            if (wOpt.isPresent()) {
                ward = wOpt.get();
                if (districtCode != null
                        && ward.getDistrictCode() != null
                        && !ward.getDistrictCode().equals(districtCode)) {
                    throw new BusinessException(
                            ErrorCode.INVALID_REQUEST,
                            String.format("Phường/Xã [%s] không thuộc Quận/Huyện [%s]", wardCode, districtCode)
                    );
                }
                if (districtCode == null && ward.getDistrictCode() != null && !ward.getDistrictCode().isBlank()) {
                    districtCode = ward.getDistrictCode();
                    if (districtRepository != null) {
                        district = districtRepository.findById(districtCode).orElse(null);
                        if (district != null
                                && provinceCode != null
                                && district.getProvinceCode() != null
                                && !district.getProvinceCode().equals(provinceCode)) {
                            throw new BusinessException(
                                    ErrorCode.INVALID_REQUEST,
                                    String.format("Phường/Xã [%s] (Quận/Huyện [%s]) không thuộc Tỉnh/Thành phố [%s]",
                                            wardCode, districtCode, provinceCode)
                            );
                        }
                    }
                }
            } else if (wardRepository.count() > 0) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Không tìm thấy Phường/Xã với mã: " + wardCode
                );
            }
        }

        String provinceName = province != null ? resolveDisplayName(province.getFullName(), province.getName()) : null;
        String districtName = district != null ? resolveDisplayName(district.getFullName(), district.getName()) : null;
        String wardName = ward != null ? resolveDisplayName(ward.getFullName(), ward.getName()) : null;

        List<String> knownSuffixes = collectKnownLocationLabels(province, district, ward, provinceName, districtName, wardName);

        String candidateStreet = firstNonBlank(rawAddressDetail, rawPermanentAddress, rawContactAddress);
        String streetDetail = extractStreetOnly(candidateStreet, knownSuffixes);

        boolean hasResolvedAdminNames = wardName != null || districtName != null || provinceName != null;

        String permanentAddress;
        if (hasResolvedAdminNames) {
            String joined = composeJoinedAddress(streetDetail, wardName, districtName, provinceName);
            permanentAddress = !joined.isBlank() ? joined : firstNonBlank(rawPermanentAddress, rawContactAddress);
        } else {
            permanentAddress = firstNonBlank(rawPermanentAddress, streetDetail, rawContactAddress);
        }

        String contactAddress;
        String trimmedRawContact = cleanText(rawContactAddress);
        String trimmedRawDetail = cleanText(rawAddressDetail);
        if (trimmedRawContact == null) {
            contactAddress = permanentAddress;
        } else if (trimmedRawDetail == null || trimmedRawContact.equalsIgnoreCase(trimmedRawDetail)) {
            // Caller passed street detail in `address` or omitted `addressDetail`: promote to full joined address
            contactAddress = hasResolvedAdminNames && permanentAddress != null ? permanentAddress : trimmedRawContact;
        } else {
            // Caller passed a distinct temporary/current contact address alongside `addressDetail`
            contactAddress = trimmedRawContact;
        }

        return new ResolvedCustomerLocation(
                provinceCode,
                province,
                provinceName,
                districtCode,
                district,
                districtName,
                wardCode,
                ward,
                wardName,
                streetDetail,
                permanentAddress,
                contactAddress
        );
    }

    public static String composeJoinedAddress(
            String streetDetail,
            String wardName,
            String districtName,
            String provinceName
    ) {
        List<String> parts = new ArrayList<>();
        if (streetDetail != null && !streetDetail.isBlank()) {
            for (String seg : streetDetail.split(",")) {
                String trimmed = seg.trim();
                if (!trimmed.isEmpty() && !containsSegmentIgnoreCase(parts, trimmed)) {
                    parts.add(trimmed);
                }
            }
        }
        addSegmentIfAbsent(parts, wardName);
        addSegmentIfAbsent(parts, districtName);
        addSegmentIfAbsent(parts, provinceName);
        return String.join(", ", parts);
    }

    public static String extractStreetOnly(String rawAddress, List<String> knownSuffixes) {
        String cleaned = cleanText(rawAddress);
        if (cleaned == null) {
            return null;
        }
        if (knownSuffixes == null || knownSuffixes.isEmpty() || !cleaned.contains(",")) {
            return cleaned;
        }
        String[] rawSegments = cleaned.split(",");
        int endExclusive = rawSegments.length;
        while (endExclusive > 1) {
            String tail = rawSegments[endExclusive - 1].trim();
            if (matchesAnyLocationLabel(tail, knownSuffixes)) {
                endExclusive--;
            } else {
                break;
            }
        }
        List<String> kept = new ArrayList<>(endExclusive);
        for (int i = 0; i < endExclusive; i++) {
            String seg = rawSegments[i].trim();
            if (!seg.isEmpty()) {
                kept.add(seg);
            }
        }
        return kept.isEmpty() ? cleaned : String.join(", ", kept);
    }

    public static String cleanCode(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String cleanText(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c.trim();
            }
        }
        return null;
    }

    public static String resolveDisplayName(String fullName, String shortName) {
        if (fullName != null && !fullName.isBlank()) {
            return fullName.trim();
        }
        return shortName != null && !shortName.isBlank() ? shortName.trim() : null;
    }

    private static void addSegmentIfAbsent(List<String> parts, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return;
        }
        String trimmed = candidate.trim();
        if (!containsSegmentIgnoreCase(parts, trimmed)) {
            parts.add(trimmed);
        }
    }

    private static boolean containsSegmentIgnoreCase(List<String> parts, String candidate) {
        for (String existing : parts) {
            if (existing.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAnyLocationLabel(String segment, List<String> knownSuffixes) {
        if (segment == null || segment.isBlank()) {
            return true;
        }
        for (String suffix : knownSuffixes) {
            if (suffix != null && !suffix.isBlank() && segment.equalsIgnoreCase(suffix.trim())) {
                return true;
            }
        }
        return false;
    }

    private static List<String> collectKnownLocationLabels(
            ProvinceEntity province,
            DistrictEntity district,
            WardEntity ward,
            String provinceName,
            String districtName,
            String wardName
    ) {
        List<String> labels = new ArrayList<>();
        if (provinceName != null) labels.add(provinceName);
        if (province != null && province.getName() != null) {
            labels.add(province.getName());
            labels.add("TP. " + province.getName());
            labels.add("TP." + province.getName());
        }
        if (districtName != null) labels.add(districtName);
        if (district != null && district.getName() != null) labels.add(district.getName());
        if (wardName != null) labels.add(wardName);
        if (ward != null && ward.getName() != null) labels.add(ward.getName());
        return labels;
    }

    private String extractExistingCustomerStreet(CustomerEntity customer) {
        ProvinceEntity p = customer.getProvince();
        if (p == null && provinceRepository != null && customer.getProvinceCode() != null) {
            p = provinceRepository.findById(customer.getProvinceCode()).orElse(null);
        }
        DistrictEntity d = customer.getDistrict();
        if (d == null && districtRepository != null && customer.getDistrictCode() != null) {
            d = districtRepository.findById(customer.getDistrictCode()).orElse(null);
        }
        WardEntity w = customer.getWard();
        if (w == null && wardRepository != null && customer.getWardCode() != null) {
            w = wardRepository.findById(customer.getWardCode()).orElse(null);
        }
        String pName = p != null ? resolveDisplayName(p.getFullName(), p.getName()) : null;
        String dName = d != null ? resolveDisplayName(d.getFullName(), d.getName()) : null;
        String wName = w != null ? resolveDisplayName(w.getFullName(), w.getName()) : null;
        List<String> suffixes = collectKnownLocationLabels(p, d, w, pName, dName, wName);
        return extractStreetOnly(firstNonBlank(customer.getPermanentAddress(), customer.getAddress()), suffixes);
    }

    private CustomerResponse mapToResponse(CustomerEntity entity) {
        ProvinceEntity province = entity.getProvince();
        if (province == null && provinceRepository != null && entity.getProvinceCode() != null) {
            province = provinceRepository.findById(entity.getProvinceCode()).orElse(null);
        }
        DistrictEntity district = entity.getDistrict();
        if (district == null && districtRepository != null && entity.getDistrictCode() != null) {
            district = districtRepository.findById(entity.getDistrictCode()).orElse(null);
        }
        WardEntity ward = entity.getWard();
        if (ward == null && wardRepository != null && entity.getWardCode() != null) {
            ward = wardRepository.findById(entity.getWardCode()).orElse(null);
        }

        String provinceName = province != null ? resolveDisplayName(province.getFullName(), province.getName()) : null;
        String districtName = district != null ? resolveDisplayName(district.getFullName(), district.getName()) : null;
        String wardName = ward != null ? resolveDisplayName(ward.getFullName(), ward.getName()) : null;

        List<String> suffixes = collectKnownLocationLabels(province, district, ward, provinceName, districtName, wardName);
        String baseAddress = firstNonBlank(entity.getPermanentAddress(), entity.getAddress());
        String addressDetail = extractStreetOnly(baseAddress, suffixes);

        String fullAddress;
        if (wardName != null || districtName != null || provinceName != null) {
            fullAddress = composeJoinedAddress(addressDetail, wardName, districtName, provinceName);
        } else {
            fullAddress = baseAddress;
        }

        String effectivePermanentAddress = entity.getPermanentAddress() != null && !entity.getPermanentAddress().isBlank()
                ? entity.getPermanentAddress()
                : fullAddress;

        return CustomerResponse.builder()
                .id(entity.getId())
                .customerCode(entity.getCustomerCode())
                .fullName(entity.getFullName())
                .identityNumber(entity.getIdentityNumber())
                .identityIssueDate(entity.getIdentityIssueDate())
                .identityIssuePlace(entity.getIdentityIssuePlace())
                .phoneNumber(entity.getPhoneNumber())
                .email(entity.getEmail())
                .dateOfBirth(entity.getDateOfBirth())
                .gender(entity.getGender())
                .provinceCode(entity.getProvinceCode())
                .provinceName(provinceName)
                .districtCode(entity.getDistrictCode())
                .districtName(districtName)
                .wardCode(entity.getWardCode())
                .wardName(wardName)
                .addressDetail(addressDetail)
                .address(entity.getAddress())
                .permanentAddress(effectivePermanentAddress)
                .fullAddress(fullAddress)
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
                .verificationDocUrlList(fileStorageService != null
                        ? fileStorageService.resolveImageUrlsFromStoredValue(entity.getVerificationDocUrls())
                        : entity.getVerificationDocUrlList())
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

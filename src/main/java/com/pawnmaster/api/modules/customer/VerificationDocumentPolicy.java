package com.pawnmaster.api.modules.customer;

import com.pawnmaster.api.common.BusinessException;
import com.pawnmaster.api.common.ErrorCode;
import com.pawnmaster.api.modules.customer.dto.OccupationDocumentPolicyItemDto;
import com.pawnmaster.api.modules.customer.dto.UpsertOccupationDocPolicyRequest;
import com.pawnmaster.api.modules.customer.dto.UpsertVerificationDocTypeRequest;
import com.pawnmaster.api.modules.customer.dto.VerificationDocumentMatrixResponse;
import com.pawnmaster.api.modules.customer.dto.VerificationDocumentTypeDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class VerificationDocumentPolicy {

    private static final Set<String> VALID_CATEGORIES = Set.of("INCOME", "RESIDENCE", "BUSINESS", "OTHER");

    private static final Map<CustomerOccupationGroup, List<VerificationDocumentType>> FALLBACK_DOCS = new EnumMap<>(
            CustomerOccupationGroup.class);

    static {
        FALLBACK_DOCS.put(CustomerOccupationGroup.OFFICE_WORKER, List.of(
                VerificationDocumentType.PAYSLIP_BANK_STATEMENT,
                VerificationDocumentType.LABOR_CONTRACT,
                VerificationDocumentType.SOCIAL_INSURANCE_VSSID
        ));
        FALLBACK_DOCS.put(CustomerOccupationGroup.FACTORY_WORKER, List.of(
                VerificationDocumentType.PAYSLIP_BANK_STATEMENT,
                VerificationDocumentType.WORKER_ID_OR_CONTRACT,
                VerificationDocumentType.SOCIAL_INSURANCE_VSSID
        ));
        FALLBACK_DOCS.put(CustomerOccupationGroup.TECH_DRIVER, List.of(
                VerificationDocumentType.DRIVER_APP_INCOME_SCREENSHOT,
                VerificationDocumentType.RESIDENCE_CONFIRMATION_CT07,
                VerificationDocumentType.RENTAL_CONTRACT
        ));
        FALLBACK_DOCS.put(CustomerOccupationGroup.SMALL_MERCHANT, List.of(
                VerificationDocumentType.BUSINESS_LICENSE_OR_STALL_LEASE,
                VerificationDocumentType.SHOP_PHOTO_AND_CASHFLOW
        ));
        FALLBACK_DOCS.put(CustomerOccupationGroup.ONLINE_SELLER, List.of(
                VerificationDocumentType.ECOMMERCE_REVENUE_SCREENSHOT,
                VerificationDocumentType.BANK_COD_STATEMENT
        ));
        FALLBACK_DOCS.put(CustomerOccupationGroup.FREELANCER, List.of(
                VerificationDocumentType.RESIDENCE_CONFIRMATION_CT07,
                VerificationDocumentType.RENTAL_CONTRACT,
                VerificationDocumentType.UTILITY_BILL
        ));
    }

    private final VerificationDocumentTypeRepository docTypeRepository;
    private final OccupationDocumentPolicyRepository policyRepository;

    @Autowired
    public VerificationDocumentPolicy(
            @Autowired(required = false) VerificationDocumentTypeRepository docTypeRepository,
            @Autowired(required = false) OccupationDocumentPolicyRepository policyRepository) {
        this.docTypeRepository = docTypeRepository;
        this.policyRepository = policyRepository;
    }

    public VerificationDocumentPolicy() {
        this(null, null);
    }

    public static String normalizeDocCode(String rawCode) {
        if (rawCode == null) {
            return null;
        }
        String trimmed = rawCode.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private List<VerificationDocumentTypeEntity> loadDbDocumentTypes() {
        if (docTypeRepository == null || policyRepository == null) {
            return Collections.emptyList();
        }
        List<VerificationDocumentTypeEntity> all = docTypeRepository.findAllByDeletedFalseOrderByDisplayOrderAscIdAsc();
        return all != null ? all : Collections.emptyList();
    }

    @Transactional(readOnly = true)
    public boolean isDocumentCodeValidForGroup(CustomerOccupationGroup group, String docCode) {
        String normalizedCode = normalizeDocCode(docCode);
        if (group == null || normalizedCode == null) {
            return false;
        }
        List<VerificationDocumentTypeEntity> dbTypes = loadDbDocumentTypes();
        if (!dbTypes.isEmpty()) {
            boolean typeActive = dbTypes.stream()
                    .anyMatch(t -> t.isActive() && normalizedCode.equalsIgnoreCase(t.getDocCode()));
            if (!typeActive) {
                return false;
            }
            List<OccupationDocumentPolicyEntity> policies = policyRepository
                    .findAllByOccupationGroupAndActiveTrueAndDeletedFalseOrderByDisplayOrderAscIdAsc(group);
            return policies != null && policies.stream()
                    .anyMatch(p -> normalizedCode.equalsIgnoreCase(p.getDocCode()));
        }

        VerificationDocumentType enumType = VerificationDocumentType.fromCodeOrNull(normalizedCode);
        if (enumType == null) {
            return false;
        }
        return FALLBACK_DOCS.getOrDefault(group, Collections.emptyList()).contains(enumType);
    }

    public boolean isDocumentValidForGroup(CustomerOccupationGroup group, VerificationDocumentType docType) {
        return docType != null && isDocumentCodeValidForGroup(group, docType.name());
    }

    @Transactional(readOnly = true)
    public List<String> getAllowedDocumentCodes(CustomerOccupationGroup group) {
        if (group == null) {
            return Collections.emptyList();
        }
        List<VerificationDocumentTypeEntity> dbTypes = loadDbDocumentTypes();
        if (!dbTypes.isEmpty()) {
            Set<String> activeTypeCodes = dbTypes.stream()
                    .filter(VerificationDocumentTypeEntity::isActive)
                    .map(t -> normalizeDocCode(t.getDocCode()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            List<OccupationDocumentPolicyEntity> policies = policyRepository
                    .findAllByOccupationGroupAndActiveTrueAndDeletedFalseOrderByDisplayOrderAscIdAsc(group);
            if (policies == null) {
                return Collections.emptyList();
            }
            return policies.stream()
                    .map(p -> normalizeDocCode(p.getDocCode()))
                    .filter(code -> code != null && activeTypeCodes.contains(code))
                    .distinct()
                    .toList();
        }

        return FALLBACK_DOCS.getOrDefault(group, Collections.emptyList()).stream()
                .map(Enum::name)
                .toList();
    }

    public Set<VerificationDocumentType> getAllowedDocuments(CustomerOccupationGroup group) {
        if (group == null) {
            return Collections.emptySet();
        }
        return getAllowedDocumentCodes(group).stream()
                .map(VerificationDocumentType::fromCodeOrNull)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional(readOnly = true)
    public String getDefaultDocumentCode(CustomerOccupationGroup group) {
        if (group == null) {
            return VerificationDocumentType.PAYSLIP_BANK_STATEMENT.name();
        }
        List<VerificationDocumentTypeEntity> dbTypes = loadDbDocumentTypes();
        if (!dbTypes.isEmpty()) {
            Set<String> activeTypeCodes = dbTypes.stream()
                    .filter(VerificationDocumentTypeEntity::isActive)
                    .map(t -> normalizeDocCode(t.getDocCode()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            List<OccupationDocumentPolicyEntity> policies = policyRepository
                    .findAllByOccupationGroupAndActiveTrueAndDeletedFalseOrderByDisplayOrderAscIdAsc(group);
            if (policies != null) {
                String explicitDefault = policies.stream()
                        .filter(OccupationDocumentPolicyEntity::isDefaultOption)
                        .map(p -> normalizeDocCode(p.getDocCode()))
                        .filter(code -> code != null && activeTypeCodes.contains(code))
                        .findFirst()
                        .orElse(null);
                if (explicitDefault != null) {
                    return explicitDefault;
                }
                String firstActive = policies.stream()
                        .map(p -> normalizeDocCode(p.getDocCode()))
                        .filter(code -> code != null && activeTypeCodes.contains(code))
                        .findFirst()
                        .orElse(null);
                if (firstActive != null) {
                    return firstActive;
                }
            }
        }
        List<String> fallback = getAllowedDocumentCodes(group);
        return fallback.isEmpty() ? VerificationDocumentType.PAYSLIP_BANK_STATEMENT.name() : fallback.get(0);
    }

    @Transactional(readOnly = true)
    public String resolveDocumentName(String docCode) {
        String normalizedCode = normalizeDocCode(docCode);
        if (normalizedCode == null) {
            return null;
        }
        List<VerificationDocumentTypeEntity> dbTypes = loadDbDocumentTypes();
        if (!dbTypes.isEmpty()) {
            String dbName = dbTypes.stream()
                    .filter(t -> normalizedCode.equalsIgnoreCase(t.getDocCode()))
                    .map(VerificationDocumentTypeEntity::getDocName)
                    .findFirst()
                    .orElse(null);
            if (dbName != null && !dbName.isBlank()) {
                return dbName;
            }
        }
        VerificationDocumentType enumType = VerificationDocumentType.fromCodeOrNull(normalizedCode);
        return enumType != null ? enumType.getDescription() : normalizedCode;
    }

    @Transactional(readOnly = true)
    public boolean isKnownDocumentCode(String docCode) {
        String normalizedCode = normalizeDocCode(docCode);
        if (normalizedCode == null) {
            return false;
        }
        List<VerificationDocumentTypeEntity> dbTypes = loadDbDocumentTypes();
        if (!dbTypes.isEmpty()) {
            return dbTypes.stream().anyMatch(t -> normalizedCode.equalsIgnoreCase(t.getDocCode()));
        }
        return VerificationDocumentType.fromCodeOrNull(normalizedCode) != null;
    }

    @Transactional(readOnly = true)
    public void validateVerificationDocumentsByCode(CustomerOccupationGroup group, String docCode, Collection<String> docUrls) {
        String normalizedCode = normalizeDocCode(docCode);
        if (docUrls == null || docUrls.isEmpty() || normalizedCode == null) {
            throw new BusinessException(ErrorCode.MISSING_VERIFICATION_DOCUMENTS,
                    "Bắt buộc phải tải lên hình ảnh chứng từ xác minh thu nhập/cư trú theo nhóm nghề nghiệp");
        }
        boolean hasNonEmptyUrl = docUrls.stream().anyMatch(url -> url != null && !url.trim().isEmpty());
        if (!hasNonEmptyUrl) {
            throw new BusinessException(ErrorCode.MISSING_VERIFICATION_DOCUMENTS,
                    "Bắt buộc phải tải lên hình ảnh chứng từ xác minh thu nhập/cư trú theo nhóm nghề nghiệp");
        }
        if (!isDocumentCodeValidForGroup(group, normalizedCode)) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_DOCUMENT,
                    "Loại chứng từ " + resolveDocumentName(normalizedCode) + " không phù hợp với nhóm "
                            + (group != null ? group.getDescription() : ""));
        }
    }

    public void validateVerificationDocuments(CustomerOccupationGroup group, VerificationDocumentType docType,
            Collection<String> docUrls) {
        validateVerificationDocumentsByCode(group, docType != null ? docType.name() : null, docUrls);
    }

    @Transactional(readOnly = true)
    public void validateExistingCustomerVerificationForContract(
            CustomerOccupationGroup group,
            String docCode,
            Collection<String> docUrls) {
        String normalizedCode = normalizeDocCode(docCode);
        if (docUrls == null || docUrls.isEmpty() || normalizedCode == null) {
            throw new BusinessException(ErrorCode.MISSING_VERIFICATION_DOCUMENTS,
                    "Bắt buộc phải tải lên hình ảnh chứng từ xác minh thu nhập/cư trú theo nhóm nghề nghiệp");
        }
        boolean hasNonEmptyUrl = docUrls.stream().anyMatch(url -> url != null && !url.trim().isEmpty());
        if (!hasNonEmptyUrl) {
            throw new BusinessException(ErrorCode.MISSING_VERIFICATION_DOCUMENTS,
                    "Bắt buộc phải tải lên hình ảnh chứng từ xác minh thu nhập/cư trú theo nhóm nghề nghiệp");
        }
        if (isDocumentCodeValidForGroup(group, normalizedCode)) {
            return;
        }
        if (isKnownDocumentCode(normalizedCode)) {
            // Historical verified customer dossier preserved when doc type or mapping was later deactivated
            return;
        }
        throw new BusinessException(ErrorCode.INVALID_VERIFICATION_DOCUMENT,
                "Loại chứng từ " + resolveDocumentName(normalizedCode) + " không phù hợp với nhóm "
                        + (group != null ? group.getDescription() : ""));
    }

    @Transactional(readOnly = true)
    public List<VerificationDocumentTypeDto> getDocumentTypes(CustomerOccupationGroup occupationGroup,
            boolean includeInactive) {
        List<VerificationDocumentTypeEntity> dbTypes = loadDbDocumentTypes();
        Map<String, VerificationDocumentTypeEntity> typeByCode = new LinkedHashMap<>();

        if (!dbTypes.isEmpty()) {
            for (VerificationDocumentTypeEntity entity : dbTypes) {
                if (includeInactive || entity.isActive()) {
                    typeByCode.put(normalizeDocCode(entity.getDocCode()), entity);
                }
            }
            if (occupationGroup == null) {
                return typeByCode.values().stream()
                        .map(t -> VerificationDocumentTypeDto.builder()
                                .code(normalizeDocCode(t.getDocCode()))
                                .name(t.getDocName())
                                .docCategory(t.getDocCategory())
                                .description(t.getDescription())
                                .displayOrder(t.getDisplayOrder())
                                .active(t.isActive())
                                .defaultForGroup(null)
                                .build())
                        .toList();
            }

            List<OccupationDocumentPolicyEntity> policies = includeInactive
                    ? policyRepository.findAllByOccupationGroupAndDeletedFalseOrderByDisplayOrderAscIdAsc(occupationGroup)
                    : policyRepository.findAllByOccupationGroupAndActiveTrueAndDeletedFalseOrderByDisplayOrderAscIdAsc(
                            occupationGroup);
            List<VerificationDocumentTypeDto> result = new ArrayList<>();
            if (policies != null) {
                for (OccupationDocumentPolicyEntity p : policies) {
                    String code = normalizeDocCode(p.getDocCode());
                    VerificationDocumentTypeEntity t = typeByCode.get(code);
                    if (t == null) {
                        continue;
                    }
                    result.add(VerificationDocumentTypeDto.builder()
                            .code(code)
                            .name(t.getDocName())
                            .docCategory(t.getDocCategory())
                            .description(t.getDescription())
                            .displayOrder(p.getDisplayOrder())
                            .active(t.isActive() && p.isActive())
                            .defaultForGroup(p.isDefaultOption())
                            .build());
                }
            }
            return result;
        }

        if (occupationGroup == null) {
            List<VerificationDocumentTypeDto> allFallback = new ArrayList<>();
            for (VerificationDocumentType d : VerificationDocumentType.values()) {
                allFallback.add(VerificationDocumentTypeDto.builder()
                        .code(d.name())
                        .name(d.getDescription())
                        .docCategory(d.getDocCategory())
                        .description(d.getDescription())
                        .displayOrder(d.getDisplayOrder())
                        .active(true)
                        .defaultForGroup(null)
                        .build());
            }
            return allFallback;
        }

        List<VerificationDocumentType> groupDocs = FALLBACK_DOCS.getOrDefault(occupationGroup, Collections.emptyList());
        List<VerificationDocumentTypeDto> result = new ArrayList<>();
        for (int i = 0; i < groupDocs.size(); i++) {
            VerificationDocumentType d = groupDocs.get(i);
            result.add(VerificationDocumentTypeDto.builder()
                    .code(d.name())
                    .name(d.getDescription())
                    .docCategory(d.getDocCategory())
                    .description(d.getDescription())
                    .displayOrder((i + 1) * 10)
                    .active(true)
                    .defaultForGroup(i == 0)
                    .build());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public VerificationDocumentMatrixResponse getPolicyMatrix(boolean includeInactive) {
        List<VerificationDocumentTypeDto> allDocTypes = getDocumentTypes(null, includeInactive);
        Map<String, VerificationDocumentTypeDto> dtoByCode = allDocTypes.stream()
                .collect(Collectors.toMap(VerificationDocumentTypeDto::getCode, d -> d, (a, b) -> a, LinkedHashMap::new));

        Map<String, List<OccupationDocumentPolicyItemDto>> byOccupation = new LinkedHashMap<>();
        List<VerificationDocumentTypeEntity> dbTypes = loadDbDocumentTypes();
        if (!dbTypes.isEmpty()) {
            for (CustomerOccupationGroup group : CustomerOccupationGroup.values()) {
                List<OccupationDocumentPolicyEntity> policies = includeInactive
                        ? policyRepository.findAllByOccupationGroupAndDeletedFalseOrderByDisplayOrderAscIdAsc(group)
                        : policyRepository.findAllByOccupationGroupAndActiveTrueAndDeletedFalseOrderByDisplayOrderAscIdAsc(
                                group);
                List<OccupationDocumentPolicyItemDto> items = new ArrayList<>();
                if (policies != null) {
                    for (OccupationDocumentPolicyEntity p : policies) {
                        String code = normalizeDocCode(p.getDocCode());
                        VerificationDocumentTypeDto docDto = dtoByCode.get(code);
                        if (docDto == null && !includeInactive) {
                            continue;
                        }
                        items.add(OccupationDocumentPolicyItemDto.builder()
                                .id(p.getId())
                                .occupationGroup(group.name())
                                .docCode(code)
                                .docName(docDto != null ? docDto.getName() : resolveDocumentName(code))
                                .docCategory(docDto != null ? docDto.getDocCategory() : "OTHER")
                                .isDefault(p.isDefaultOption())
                                .displayOrder(p.getDisplayOrder())
                                .active(p.isActive() && (docDto == null || docDto.isActive()))
                                .build());
                    }
                }
                byOccupation.put(group.name(), items);
            }
        } else {
            for (CustomerOccupationGroup group : CustomerOccupationGroup.values()) {
                List<VerificationDocumentType> groupDocs = FALLBACK_DOCS.getOrDefault(group, Collections.emptyList());
                List<OccupationDocumentPolicyItemDto> items = new ArrayList<>();
                for (int i = 0; i < groupDocs.size(); i++) {
                    VerificationDocumentType d = groupDocs.get(i);
                    items.add(OccupationDocumentPolicyItemDto.builder()
                            .occupationGroup(group.name())
                            .docCode(d.name())
                            .docName(d.getDescription())
                            .docCategory(d.getDocCategory())
                            .isDefault(i == 0)
                            .displayOrder((i + 1) * 10)
                            .active(true)
                            .build());
                }
                byOccupation.put(group.name(), items);
            }
        }

        return VerificationDocumentMatrixResponse.builder()
                .documentTypes(allDocTypes)
                .policiesByOccupation(byOccupation)
                .build();
    }

    @Transactional
    public VerificationDocumentTypeDto upsertDocumentType(UpsertVerificationDocTypeRequest request) {
        if (docTypeRepository == null || policyRepository == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Dịch vụ cấu hình chứng từ chưa kết nối CSDL");
        }
        ensureSeededIfDatabaseEmpty();

        String normalizedCode = normalizeDocCode(request.getCode());
        if (normalizedCode == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Mã chứng từ không được để trống");
        }
        String category = request.getDocCategory() != null && !request.getDocCategory().isBlank()
                ? request.getDocCategory().trim().toUpperCase(Locale.ROOT)
                : "INCOME";
        if (!VALID_CATEGORIES.contains(category)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Nhóm chứng từ không hợp lệ (chỉ hỗ trợ INCOME, RESIDENCE, BUSINESS, OTHER)");
        }

        VerificationDocumentTypeEntity entity = docTypeRepository.findByDocCodeIgnoreCase(normalizedCode)
                .orElseGet(() -> VerificationDocumentTypeEntity.builder()
                        .docCode(normalizedCode)
                        .build());
        entity.setDocCode(normalizedCode);
        entity.setDocName(request.getName().trim());
        entity.setDocCategory(category);
        entity.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        entity.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 100);
        entity.setActive(request.getActive() == null || request.getActive());
        entity.setDeleted(false);

        VerificationDocumentTypeEntity saved = docTypeRepository.save(entity);
        VerificationDocumentTypeEntity effective = saved != null ? saved : entity;
        return VerificationDocumentTypeDto.builder()
                .code(effective.getDocCode())
                .name(effective.getDocName())
                .docCategory(effective.getDocCategory())
                .description(effective.getDescription())
                .displayOrder(effective.getDisplayOrder())
                .active(effective.isActive())
                .build();
    }

    @Transactional
    public OccupationDocumentPolicyItemDto upsertOccupationDocumentPolicy(UpsertOccupationDocPolicyRequest request) {
        if (docTypeRepository == null || policyRepository == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Dịch vụ cấu hình ma trận chứng từ chưa kết nối CSDL");
        }
        ensureSeededIfDatabaseEmpty();

        CustomerOccupationGroup group = request.getOccupationGroup();
        String normalizedCode = normalizeDocCode(request.getDocCode());
        if (group == null || normalizedCode == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Nhóm nghề nghiệp và mã chứng từ không được để trống");
        }

        VerificationDocumentTypeEntity docType = docTypeRepository.findByDocCodeIgnoreCaseAndDeletedFalse(normalizedCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST,
                        "Không tìm thấy loại chứng từ với mã: " + normalizedCode));

        boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault());
        boolean makeActive = request.getActive() == null || request.getActive();
        if (makeDefault && !makeActive) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Không thể đặt làm chứng từ mặc định khi quy tắc đang tạm ngưng");
        }

        if (makeDefault) {
            List<OccupationDocumentPolicyEntity> siblings = policyRepository
                    .findAllByOccupationGroupAndDeletedFalseOrderByDisplayOrderAscIdAsc(group);
            if (siblings != null) {
                List<OccupationDocumentPolicyEntity> cleared = new ArrayList<>();
                for (OccupationDocumentPolicyEntity sibling : siblings) {
                    if (sibling.isDefaultOption() && !normalizedCode.equalsIgnoreCase(sibling.getDocCode())) {
                        sibling.setDefaultOption(false);
                        cleared.add(sibling);
                    }
                }
                if (!cleared.isEmpty()) {
                    policyRepository.saveAll(cleared);
                }
            }
        }

        OccupationDocumentPolicyEntity entity = policyRepository
                .findByOccupationGroupAndDocCodeIgnoreCase(group, normalizedCode)
                .orElseGet(() -> OccupationDocumentPolicyEntity.builder()
                        .occupationGroup(group)
                        .docCode(docType.getDocCode())
                        .build());

        entity.setOccupationGroup(group);
        entity.setDocCode(docType.getDocCode());
        entity.setDefaultOption(makeDefault);
        entity.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : docType.getDisplayOrder());
        entity.setActive(makeActive);
        entity.setDeleted(false);

        OccupationDocumentPolicyEntity saved = policyRepository.save(entity);
        OccupationDocumentPolicyEntity effective = saved != null ? saved : entity;
        return OccupationDocumentPolicyItemDto.builder()
                .id(effective.getId())
                .occupationGroup(group.name())
                .docCode(effective.getDocCode())
                .docName(docType.getDocName())
                .docCategory(docType.getDocCategory())
                .isDefault(effective.isDefaultOption())
                .displayOrder(effective.getDisplayOrder())
                .active(effective.isActive() && docType.isActive())
                .build();
    }

    private void ensureSeededIfDatabaseEmpty() {
        if (docTypeRepository.count() > 0) {
            return;
        }
        List<VerificationDocumentTypeEntity> defaultTypes = new ArrayList<>();
        for (VerificationDocumentType d : VerificationDocumentType.values()) {
            defaultTypes.add(VerificationDocumentTypeEntity.builder()
                    .docCode(d.name())
                    .docName(d.getDescription())
                    .docCategory(d.getDocCategory())
                    .description(d.getDescription())
                    .displayOrder(d.getDisplayOrder())
                    .active(true)
                    .build());
        }
        docTypeRepository.saveAll(defaultTypes);

        if (policyRepository.count() == 0) {
            List<OccupationDocumentPolicyEntity> defaultPolicies = new ArrayList<>();
            for (Map.Entry<CustomerOccupationGroup, List<VerificationDocumentType>> entry : FALLBACK_DOCS.entrySet()) {
                List<VerificationDocumentType> docs = entry.getValue();
                for (int i = 0; i < docs.size(); i++) {
                    defaultPolicies.add(OccupationDocumentPolicyEntity.builder()
                            .occupationGroup(entry.getKey())
                            .docCode(docs.get(i).name())
                            .defaultOption(i == 0)
                            .displayOrder((i + 1) * 10)
                            .active(true)
                            .build());
                }
            }
            policyRepository.saveAll(defaultPolicies);
        }
    }
}

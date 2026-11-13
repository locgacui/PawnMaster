package com.pawnmaster.api.modules.customer;

import org.mockito.Mockito;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;

/**
 * In-memory Mockito-backed helper for {@link VerificationDocumentPolicy} unit tests.
 * Uses lenient stubbing to stay compatible with strict JUnit 5 {@code MockitoExtension}.
 */
public final class VerificationDocumentPolicyTestHelper {

    public static final String PAYSLIP_BANK_STATEMENT = "PAYSLIP_BANK_STATEMENT";
    public static final String LABOR_CONTRACT = "LABOR_CONTRACT";
    public static final String SOCIAL_INSURANCE_VSSID = "SOCIAL_INSURANCE_VSSID";
    public static final String WORKER_ID_OR_CONTRACT = "WORKER_ID_OR_CONTRACT";
    public static final String DRIVER_APP_INCOME_SCREENSHOT = "DRIVER_APP_INCOME_SCREENSHOT";
    public static final String RESIDENCE_CONFIRMATION_CT07 = "RESIDENCE_CONFIRMATION_CT07";
    public static final String RENTAL_CONTRACT = "RENTAL_CONTRACT";
    public static final String UTILITY_BILL = "UTILITY_BILL";
    public static final String BUSINESS_LICENSE_OR_STALL_LEASE = "BUSINESS_LICENSE_OR_STALL_LEASE";
    public static final String SHOP_PHOTO_AND_CASHFLOW = "SHOP_PHOTO_AND_CASHFLOW";
    public static final String ECOMMERCE_REVENUE_SCREENSHOT = "ECOMMERCE_REVENUE_SCREENSHOT";
    public static final String BANK_COD_STATEMENT = "BANK_COD_STATEMENT";

    public static final List<String> ALL_DEFAULT_DOC_CODES = List.of(
            PAYSLIP_BANK_STATEMENT,
            LABOR_CONTRACT,
            SOCIAL_INSURANCE_VSSID,
            WORKER_ID_OR_CONTRACT,
            DRIVER_APP_INCOME_SCREENSHOT,
            RESIDENCE_CONFIRMATION_CT07,
            RENTAL_CONTRACT,
            UTILITY_BILL,
            BUSINESS_LICENSE_OR_STALL_LEASE,
            SHOP_PHOTO_AND_CASHFLOW,
            ECOMMERCE_REVENUE_SCREENSHOT,
            BANK_COD_STATEMENT
    );

    private record DocTypeSeed(String code, String name, String category, int displayOrder) {
    }

    private static final List<DocTypeSeed> DEFAULT_DOC_TYPES = List.of(
            new DocTypeSeed(PAYSLIP_BANK_STATEMENT, "Bảng lương / Sao kê ngân hàng 3 tháng", "INCOME", 10),
            new DocTypeSeed(LABOR_CONTRACT, "Hợp đồng lao động / Quyết định biên chế", "INCOME", 20),
            new DocTypeSeed(SOCIAL_INSURANCE_VSSID, "Ảnh màn hình VssID quá trình BHXH", "INCOME", 30),
            new DocTypeSeed(WORKER_ID_OR_CONTRACT, "Thẻ công nhân + HĐLĐ", "INCOME", 40),
            new DocTypeSeed(DRIVER_APP_INCOME_SCREENSHOT, "Ảnh app tài xế & thu nhập 4 tuần", "INCOME", 50),
            new DocTypeSeed(RESIDENCE_CONFIRMATION_CT07, "Xác nhận thông tin cư trú CT07 / VNeID mức 2", "RESIDENCE", 60),
            new DocTypeSeed(RENTAL_CONTRACT, "Hợp đồng thuê nhà / Sổ tạm trú", "RESIDENCE", 70),
            new DocTypeSeed(UTILITY_BILL, "Hóa đơn Điện / Nước / Internet", "RESIDENCE", 80),
            new DocTypeSeed(BUSINESS_LICENSE_OR_STALL_LEASE, "Giấy phép ĐKKD / HĐ thuê sạp chợ", "BUSINESS", 90),
            new DocTypeSeed(SHOP_PHOTO_AND_CASHFLOW, "Ảnh quầy sạp thực tế & sổ thu chi", "BUSINESS", 100),
            new DocTypeSeed(ECOMMERCE_REVENUE_SCREENSHOT, "Ảnh doanh thu sàn TMĐT", "BUSINESS", 110),
            new DocTypeSeed(BANK_COD_STATEMENT, "Sao kê nhận tiền đối soát COD", "BUSINESS", 120)
    );

    private static final Map<CustomerOccupationGroup, List<String>> DEFAULT_MATRIX = new EnumMap<>(
            CustomerOccupationGroup.class);

    static {
        DEFAULT_MATRIX.put(CustomerOccupationGroup.OFFICE_WORKER, List.of(
                PAYSLIP_BANK_STATEMENT, LABOR_CONTRACT, SOCIAL_INSURANCE_VSSID));
        DEFAULT_MATRIX.put(CustomerOccupationGroup.FACTORY_WORKER, List.of(
                PAYSLIP_BANK_STATEMENT, WORKER_ID_OR_CONTRACT, SOCIAL_INSURANCE_VSSID));
        DEFAULT_MATRIX.put(CustomerOccupationGroup.TECH_DRIVER, List.of(
                DRIVER_APP_INCOME_SCREENSHOT, RESIDENCE_CONFIRMATION_CT07, RENTAL_CONTRACT));
        DEFAULT_MATRIX.put(CustomerOccupationGroup.SMALL_MERCHANT, List.of(
                BUSINESS_LICENSE_OR_STALL_LEASE, SHOP_PHOTO_AND_CASHFLOW));
        DEFAULT_MATRIX.put(CustomerOccupationGroup.ONLINE_SELLER, List.of(
                ECOMMERCE_REVENUE_SCREENSHOT, BANK_COD_STATEMENT));
        DEFAULT_MATRIX.put(CustomerOccupationGroup.FREELANCER, List.of(
                RESIDENCE_CONFIRMATION_CT07, RENTAL_CONTRACT, UTILITY_BILL));
    }

    private VerificationDocumentPolicyTestHelper() {
    }

    public static void seedDefaultCatalogAndMatrix(
            Map<String, VerificationDocumentTypeEntity> docTypeStore,
            Map<String, OccupationDocumentPolicyEntity> policyStore,
            AtomicLong seq) {
        for (DocTypeSeed seed : DEFAULT_DOC_TYPES) {
            VerificationDocumentTypeEntity entity = VerificationDocumentTypeEntity.builder()
                    .docCode(seed.code())
                    .docName(seed.name())
                    .docCategory(seed.category())
                    .description(seed.name())
                    .displayOrder(seed.displayOrder())
                    .active(true)
                    .build();
            entity.setId(seq.incrementAndGet());
            docTypeStore.put(seed.code(), entity);
        }

        for (CustomerOccupationGroup group : CustomerOccupationGroup.values()) {
            List<String> defaults = DEFAULT_MATRIX.getOrDefault(group, List.of());
            for (int i = 0; i < defaults.size(); i++) {
                String code = defaults.get(i);
                OccupationDocumentPolicyEntity policy = OccupationDocumentPolicyEntity.builder()
                        .occupationGroup(group)
                        .docCode(code)
                        .defaultOption(i == 0)
                        .displayOrder((i + 1) * 10)
                        .active(true)
                        .build();
                policy.setId(seq.incrementAndGet());
                policyStore.put(group.name() + "::" + code, policy);
            }
        }
    }

    public static VerificationDocumentPolicy createMockBackedPolicy() {
        Map<String, VerificationDocumentTypeEntity> docTypeStore = new LinkedHashMap<>();
        Map<String, OccupationDocumentPolicyEntity> policyStore = new LinkedHashMap<>();
        AtomicLong seq = new AtomicLong(100L);

        seedDefaultCatalogAndMatrix(docTypeStore, policyStore, seq);

        VerificationDocumentTypeRepository docTypeRepository = mock(VerificationDocumentTypeRepository.class);
        OccupationDocumentPolicyRepository policyRepository = mock(OccupationDocumentPolicyRepository.class);

        Mockito.lenient().when(docTypeRepository.count()).thenAnswer(inv -> (long) docTypeStore.size());
        Mockito.lenient().when(docTypeRepository.save(any(VerificationDocumentTypeEntity.class))).thenAnswer(inv -> {
            VerificationDocumentTypeEntity e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(seq.incrementAndGet());
            }
            docTypeStore.put(e.getDocCode().toUpperCase(), e);
            return e;
        });
        Mockito.lenient().when(docTypeRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<VerificationDocumentTypeEntity> list = inv.getArgument(0);
            for (VerificationDocumentTypeEntity e : list) {
                if (e.getId() == null) {
                    e.setId(seq.incrementAndGet());
                }
                docTypeStore.put(e.getDocCode().toUpperCase(), e);
            }
            return list;
        });
        Mockito.lenient().when(docTypeRepository.findAllByDeletedFalseOrderByDisplayOrderAscIdAsc()).thenAnswer(inv ->
                docTypeStore.values().stream()
                        .filter(e -> !e.isDeleted())
                        .sorted(Comparator.comparingInt(VerificationDocumentTypeEntity::getDisplayOrder)
                                .thenComparing(e -> e.getId() != null ? e.getId() : 0L))
                        .toList());
        Mockito.lenient().when(docTypeRepository.findByDocCodeIgnoreCase(anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            return Optional.ofNullable(docTypeStore.get(code.trim().toUpperCase()));
        });
        Mockito.lenient().when(docTypeRepository.findByDocCodeIgnoreCaseAndDeletedFalse(anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            VerificationDocumentTypeEntity hit = docTypeStore.get(code.trim().toUpperCase());
            return (hit != null && !hit.isDeleted()) ? Optional.of(hit) : Optional.empty();
        });

        Mockito.lenient().when(policyRepository.count()).thenAnswer(inv -> (long) policyStore.size());
        Mockito.lenient().when(policyRepository.save(any(OccupationDocumentPolicyEntity.class))).thenAnswer(inv -> {
            OccupationDocumentPolicyEntity p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(seq.incrementAndGet());
            }
            policyStore.put(p.getOccupationGroup().name() + "::" + p.getDocCode().toUpperCase(), p);
            return p;
        });
        Mockito.lenient().when(policyRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<OccupationDocumentPolicyEntity> list = inv.getArgument(0);
            for (OccupationDocumentPolicyEntity p : list) {
                if (p.getId() == null) {
                    p.setId(seq.incrementAndGet());
                }
                policyStore.put(p.getOccupationGroup().name() + "::" + p.getDocCode().toUpperCase(), p);
            }
            return list;
        });
        Mockito.lenient().when(policyRepository.findByOccupationGroupAndDocCodeIgnoreCase(any(), anyString())).thenAnswer(inv -> {
            CustomerOccupationGroup g = inv.getArgument(0);
            String code = inv.getArgument(1);
            return Optional.ofNullable(policyStore.get(g.name() + "::" + code.trim().toUpperCase()));
        });
        Mockito.lenient().when(policyRepository.findAllByOccupationGroupAndDeletedFalseOrderByDisplayOrderAscIdAsc(any()))
                .thenAnswer(inv -> {
                    CustomerOccupationGroup g = inv.getArgument(0);
                    return policyStore.values().stream()
                            .filter(p -> !p.isDeleted() && p.getOccupationGroup() == g)
                            .sorted(Comparator.comparingInt(OccupationDocumentPolicyEntity::getDisplayOrder)
                                    .thenComparing(p -> p.getId() != null ? p.getId() : 0L))
                            .toList();
                });
        Mockito.lenient().when(policyRepository.findAllByOccupationGroupAndActiveTrueAndDeletedFalseOrderByDisplayOrderAscIdAsc(any()))
                .thenAnswer(inv -> {
                    CustomerOccupationGroup g = inv.getArgument(0);
                    return policyStore.values().stream()
                            .filter(p -> !p.isDeleted() && p.isActive() && p.getOccupationGroup() == g)
                            .sorted(Comparator.comparingInt(OccupationDocumentPolicyEntity::getDisplayOrder)
                                    .thenComparing(p -> p.getId() != null ? p.getId() : 0L))
                            .toList();
                });

        return new VerificationDocumentPolicy(docTypeRepository, policyRepository);
    }
}


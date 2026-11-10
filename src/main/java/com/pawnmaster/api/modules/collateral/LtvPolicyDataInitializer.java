package com.pawnmaster.api.modules.collateral;

import com.pawnmaster.api.modules.customer.CustomerOccupationGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LtvPolicyDataInitializer {

    private final LtvPolicyRepository ltvPolicyRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationReady() {
        if (ltvPolicyRepository.count() == 0) {
            log.info("LtvPolicy table is empty. Seeding all 48 default LTV policy matrix combinations...");
            seedDefaultLtvPolicies();
        }
    }

    @Transactional
    public List<LtvPolicyEntity> seedDefaultLtvPolicies() {
        List<LtvPolicyEntity> policies = createDefaultPolicies();
        return ltvPolicyRepository.saveAll(policies);
    }

    public static List<LtvPolicyEntity> createDefaultPolicies() {
        List<LtvPolicyEntity> list = new ArrayList<>(48);

        // 1. OFFICE_WORKER
        add(list, CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.GOLD_JEWELRY, "0.92", "Viên chức - Trang sức vàng (92%)");
        add(list, CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.MOTORBIKE, "0.90", "Viên chức - Xe máy (90%)");
        add(list, CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.CAR, "0.85", "Viên chức - Ô tô (85%)");
        add(list, CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.PHONE, "0.80", "Viên chức - Điện thoại (80%)");
        add(list, CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.LAPTOP, "0.80", "Viên chức - Laptop (80%)");
        add(list, CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.WATCH, "0.75", "Viên chức - Đồng hồ (75%)");
        add(list, CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.REAL_ESTATE, "0.75", "Viên chức - Bất động sản (75%)");
        add(list, CustomerOccupationGroup.OFFICE_WORKER, CollateralCategory.OTHER, "0.70", "Viên chức - Tài sản khác (70%)");

        // 2. FACTORY_WORKER
        add(list, CustomerOccupationGroup.FACTORY_WORKER, CollateralCategory.GOLD_JEWELRY, "0.90", "Công nhân - Trang sức vàng (90%)");
        add(list, CustomerOccupationGroup.FACTORY_WORKER, CollateralCategory.MOTORBIKE, "0.80", "Công nhân - Xe máy (80%)");
        add(list, CustomerOccupationGroup.FACTORY_WORKER, CollateralCategory.CAR, "0.75", "Công nhân - Ô tô (75%)");
        add(list, CustomerOccupationGroup.FACTORY_WORKER, CollateralCategory.PHONE, "0.75", "Công nhân - Điện thoại (75%)");
        add(list, CustomerOccupationGroup.FACTORY_WORKER, CollateralCategory.LAPTOP, "0.75", "Công nhân - Laptop (75%)");
        add(list, CustomerOccupationGroup.FACTORY_WORKER, CollateralCategory.WATCH, "0.70", "Công nhân - Đồng hồ (70%)");
        add(list, CustomerOccupationGroup.FACTORY_WORKER, CollateralCategory.REAL_ESTATE, "0.70", "Công nhân - Bất động sản (70%)");
        add(list, CustomerOccupationGroup.FACTORY_WORKER, CollateralCategory.OTHER, "0.65", "Công nhân - Tài sản khác (65%)");

        // 3. TECH_DRIVER
        add(list, CustomerOccupationGroup.TECH_DRIVER, CollateralCategory.GOLD_JEWELRY, "0.88", "Tài xế công nghệ - Trang sức vàng (88%)");
        add(list, CustomerOccupationGroup.TECH_DRIVER, CollateralCategory.MOTORBIKE, "0.70", "Tài xế công nghệ - Xe máy (70%)");
        add(list, CustomerOccupationGroup.TECH_DRIVER, CollateralCategory.CAR, "0.70", "Tài xế công nghệ - Ô tô (70%)");
        add(list, CustomerOccupationGroup.TECH_DRIVER, CollateralCategory.PHONE, "0.70", "Tài xế công nghệ - Điện thoại (70%)");
        add(list, CustomerOccupationGroup.TECH_DRIVER, CollateralCategory.LAPTOP, "0.70", "Tài xế công nghệ - Laptop (70%)");
        add(list, CustomerOccupationGroup.TECH_DRIVER, CollateralCategory.WATCH, "0.65", "Tài xế công nghệ - Đồng hồ (65%)");
        add(list, CustomerOccupationGroup.TECH_DRIVER, CollateralCategory.REAL_ESTATE, "0.65", "Tài xế công nghệ - Bất động sản (65%)");
        add(list, CustomerOccupationGroup.TECH_DRIVER, CollateralCategory.OTHER, "0.60", "Tài xế công nghệ - Tài sản khác (60%)");

        // 4. SMALL_MERCHANT
        add(list, CustomerOccupationGroup.SMALL_MERCHANT, CollateralCategory.GOLD_JEWELRY, "0.90", "Tiểu thương - Trang sức vàng (90%)");
        add(list, CustomerOccupationGroup.SMALL_MERCHANT, CollateralCategory.MOTORBIKE, "0.80", "Tiểu thương - Xe máy (80%)");
        add(list, CustomerOccupationGroup.SMALL_MERCHANT, CollateralCategory.CAR, "0.80", "Tiểu thương - Ô tô (80%)");
        add(list, CustomerOccupationGroup.SMALL_MERCHANT, CollateralCategory.PHONE, "0.75", "Tiểu thương - Điện thoại (75%)");
        add(list, CustomerOccupationGroup.SMALL_MERCHANT, CollateralCategory.LAPTOP, "0.75", "Tiểu thương - Laptop (75%)");
        add(list, CustomerOccupationGroup.SMALL_MERCHANT, CollateralCategory.WATCH, "0.75", "Tiểu thương - Đồng hồ (75%)");
        add(list, CustomerOccupationGroup.SMALL_MERCHANT, CollateralCategory.REAL_ESTATE, "0.70", "Tiểu thương - Bất động sản (70%)");
        add(list, CustomerOccupationGroup.SMALL_MERCHANT, CollateralCategory.OTHER, "0.65", "Tiểu thương - Tài sản khác (65%)");

        // 5. ONLINE_SELLER
        add(list, CustomerOccupationGroup.ONLINE_SELLER, CollateralCategory.GOLD_JEWELRY, "0.88", "Kinh doanh online - Trang sức vàng (88%)");
        add(list, CustomerOccupationGroup.ONLINE_SELLER, CollateralCategory.MOTORBIKE, "0.75", "Kinh doanh online - Xe máy (75%)");
        add(list, CustomerOccupationGroup.ONLINE_SELLER, CollateralCategory.CAR, "0.75", "Kinh doanh online - Ô tô (75%)");
        add(list, CustomerOccupationGroup.ONLINE_SELLER, CollateralCategory.PHONE, "0.70", "Kinh doanh online - Điện thoại (70%)");
        add(list, CustomerOccupationGroup.ONLINE_SELLER, CollateralCategory.LAPTOP, "0.70", "Kinh doanh online - Laptop (70%)");
        add(list, CustomerOccupationGroup.ONLINE_SELLER, CollateralCategory.WATCH, "0.68", "Kinh doanh online - Đồng hồ (68%)");
        add(list, CustomerOccupationGroup.ONLINE_SELLER, CollateralCategory.REAL_ESTATE, "0.65", "Kinh doanh online - Bất động sản (65%)");
        add(list, CustomerOccupationGroup.ONLINE_SELLER, CollateralCategory.OTHER, "0.60", "Kinh doanh online - Tài sản khác (60%)");

        // 6. FREELANCER
        add(list, CustomerOccupationGroup.FREELANCER, CollateralCategory.GOLD_JEWELRY, "0.85", "Lao động tự do - Trang sức vàng (85%)");
        add(list, CustomerOccupationGroup.FREELANCER, CollateralCategory.MOTORBIKE, "0.65", "Lao động tự do - Xe máy (65%)");
        add(list, CustomerOccupationGroup.FREELANCER, CollateralCategory.CAR, "0.65", "Lao động tự do - Ô tô (65%)");
        add(list, CustomerOccupationGroup.FREELANCER, CollateralCategory.PHONE, "0.60", "Lao động tự do - Điện thoại (60%)");
        add(list, CustomerOccupationGroup.FREELANCER, CollateralCategory.LAPTOP, "0.60", "Lao động tự do - Laptop (60%)");
        add(list, CustomerOccupationGroup.FREELANCER, CollateralCategory.WATCH, "0.55", "Lao động tự do - Đồng hồ (55%)");
        add(list, CustomerOccupationGroup.FREELANCER, CollateralCategory.REAL_ESTATE, "0.55", "Lao động tự do - Bất động sản (55%)");
        add(list, CustomerOccupationGroup.FREELANCER, CollateralCategory.OTHER, "0.50", "Lao động tự do - Tài sản khác (50%)");

        return list;
    }

    private static void add(List<LtvPolicyEntity> list, CustomerOccupationGroup group, CollateralCategory category, String percent, String desc) {
        list.add(LtvPolicyEntity.builder()
                .occupationGroup(group)
                .collateralCategory(category)
                .maxLtvPercent(new BigDecimal(percent))
                .description(desc)
                .build());
    }
}


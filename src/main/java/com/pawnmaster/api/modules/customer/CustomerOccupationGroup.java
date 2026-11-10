package com.pawnmaster.api.modules.customer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CustomerOccupationGroup {
    OFFICE_WORKER("Viên chức - Văn phòng"),
    FACTORY_WORKER("Công nhân"),
    TECH_DRIVER("Tài xế công nghệ"),
    SMALL_MERCHANT("Tiểu thương"),
    ONLINE_SELLER("Kinh doanh online"),
    FREELANCER("Lao động tự do");

    private final String description;
}

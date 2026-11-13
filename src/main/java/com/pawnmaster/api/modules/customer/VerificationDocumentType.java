package com.pawnmaster.api.modules.customer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum VerificationDocumentType {
    PAYSLIP_BANK_STATEMENT("Bảng lương / Sao kê ngân hàng 3 tháng", "INCOME", 10),
    LABOR_CONTRACT("Hợp đồng lao động / Quyết định biên chế", "INCOME", 20),
    SOCIAL_INSURANCE_VSSID("Ảnh màn hình VssID quá trình BHXH", "INCOME", 30),
    WORKER_ID_OR_CONTRACT("Thẻ công nhân + HĐLĐ", "INCOME", 40),
    DRIVER_APP_INCOME_SCREENSHOT("Ảnh app tài xế & thu nhập 4 tuần", "INCOME", 50),
    RESIDENCE_CONFIRMATION_CT07("Xác nhận thông tin cư trú CT07 / VNeID mức 2", "RESIDENCE", 60),
    RENTAL_CONTRACT("Hợp đồng thuê nhà / Sổ tạm trú", "RESIDENCE", 70),
    UTILITY_BILL("Hóa đơn Điện / Nước / Internet", "RESIDENCE", 80),
    BUSINESS_LICENSE_OR_STALL_LEASE("Giấy phép ĐKKD / HĐ thuê sạp chợ", "BUSINESS", 90),
    SHOP_PHOTO_AND_CASHFLOW("Ảnh quầy sạp thực tế & sổ thu chi", "BUSINESS", 100),
    ECOMMERCE_REVENUE_SCREENSHOT("Ảnh doanh thu sàn TMĐT", "BUSINESS", 110),
    BANK_COD_STATEMENT("Sao kê nhận tiền đối soát COD", "BUSINESS", 120);

    private final String description;
    private final String docCategory;
    private final int displayOrder;

    public static VerificationDocumentType fromCodeOrNull(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        for (VerificationDocumentType value : values()) {
            if (value.name().equals(normalized)) {
                return value;
            }
        }
        return null;
    }
}

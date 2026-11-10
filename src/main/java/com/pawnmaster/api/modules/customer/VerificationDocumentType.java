package com.pawnmaster.api.modules.customer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VerificationDocumentType {
    PAYSLIP_BANK_STATEMENT("Bảng lương / Sao kê ngân hàng 3 tháng"),
    LABOR_CONTRACT("Hợp đồng lao động / Quyết định biên chế"),
    SOCIAL_INSURANCE_VSSID("Ảnh màn hình VssID quá trình BHXH"),
    WORKER_ID_OR_CONTRACT("Thẻ công nhân + HĐLĐ"),
    DRIVER_APP_INCOME_SCREENSHOT("Ảnh app tài xế & thu nhập 4 tuần"),
    BUSINESS_LICENSE_OR_STALL_LEASE("Giấy phép ĐKKD / HĐ thuê sạp chợ"),
    SHOP_PHOTO_AND_CASHFLOW("Ảnh quầy sạp thực tế & sổ thu chi"),
    ECOMMERCE_REVENUE_SCREENSHOT("Ảnh doanh thu sàn TMĐT"),
    BANK_COD_STATEMENT("Sao kê nhận tiền đối soát COD"),
    RESIDENCE_CONFIRMATION_CT07("Xác nhận thông tin cư trú CT07 / VNeID mức 2"),
    RENTAL_CONTRACT("Hợp đồng thuê nhà / Sổ tạm trú"),
    UTILITY_BILL("Hóa đơn Điện / Nước / Internet");

    private final String description;
}

package com.pawnmaster.api.modules.contract;

public enum PaymentType {
    INTEREST_PAYMENT,  // Đóng lãi định kỳ
    PARTIAL_PRINCIPAL, // Trả bớt gốc
    REDEMPTION,        // Tất toán hợp đồng & chuộc đồ
    LIQUIDATION        // Thu tiền thanh lý tài sản
}


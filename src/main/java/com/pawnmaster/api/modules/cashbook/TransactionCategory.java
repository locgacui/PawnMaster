package com.pawnmaster.api.modules.cashbook;

public enum TransactionCategory {
    DISBURSEMENT,         // Chi giải ngân hợp đồng cầm đồ mới
    INTEREST_COLLECTION,  // Thu tiền lãi & phí lưu kho
    PRINCIPAL_COLLECTION, // Thu tiền gốc (trả bớt gốc / tất toán chuộc đồ)
    LIQUIDATION_INCOME,   // Thu tiền thanh lý tài sản
    CAPITAL_CONTRIBUTION, // Nhập quỹ đầu kỳ / Bổ sung vốn
    OPERATING_EXPENSE,    // Chi phí vận hành cửa hàng
    OTHER                 // Thu/chi khác
}


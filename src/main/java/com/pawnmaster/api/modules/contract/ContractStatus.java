package com.pawnmaster.api.modules.contract;

public enum ContractStatus {
    PENDING_APPROVAL, // Chờ Quản lý chi nhánh duyệt & giải ngân
    ACTIVE,           // Đang cầm cố trong hạn
    OVERDUE,          // Quá hạn đóng lãi / quá hạn hợp đồng
    EXTENDED,         // Đã gia hạn kỳ mới
    REDEEMED,         // Đã tất toán / chuộc tài sản
    LIQUIDATED,       // Đã thanh lý tài sản
    CANCELLED         // Hợp đồng bị hủy / từ chối duyệt
}

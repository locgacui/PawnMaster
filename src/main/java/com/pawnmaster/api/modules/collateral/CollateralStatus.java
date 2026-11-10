package com.pawnmaster.api.modules.collateral;

public enum CollateralStatus {
    IN_VAULT,            // Đang lưu kho cầm cố
    WAITING_LIQUIDATION, // Quá hạn chờ thanh lý
    REDEEMED,            // Khách đã chuộc về
    LIQUIDATED           // Cửa hàng đã thanh lý
}


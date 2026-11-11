package com.pawnmaster.api.modules.dashboard;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum DashboardContractTab {
    ALL("Tất cả"),
    PENDING_APPROVAL("Chờ duyệt"),
    ACTIVE("Đang vay"),
    DUE_TODAY("Đến hạn hôm nay"),
    OVERDUE("Quá hạn"),
    WAITING_LIQUIDATION("Chờ thanh lý");

    private final String label;

    public String getCode() {
        return name();
    }

    public static DashboardContractTab fromParam(String param) {
        if (param == null || param.isBlank()) {
            return ALL;
        }
        String clean = param.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return switch (clean) {
            case "ALL" -> ALL;
            case "PENDING_APPROVAL", "PENDING" -> PENDING_APPROVAL;
            case "ACTIVE" -> ACTIVE;
            case "DUE_TODAY", "TODAY", "DUE" -> DUE_TODAY;
            case "OVERDUE" -> OVERDUE;
            case "WAITING_LIQUIDATION", "LIQUIDATION", "LIQUIDATING" -> WAITING_LIQUIDATION;
            default -> {
                try {
                    yield DashboardContractTab.valueOf(clean);
                } catch (IllegalArgumentException ex) {
                    yield ALL;
                }
            }
        };
    }
}

package com.pawnmaster.api.modules.dashboard;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum DashboardTimeframe {
    LAST_7_DAYS("7 ngày qua", "days"),
    THIS_MONTH("Tháng này", "months"),
    LAST_MONTH("Tháng trước", "months"),
    LAST_6_MONTHS("6 tháng trước", "months"),
    THIS_YEAR("Năm hiện tại", "year");

    private final String label;
    private final String legacyChartKey;

    public String getCode() {
        return name();
    }

    public DashboardPeriod calculatePeriod() {
        return DashboardPeriod.of(this);
    }

    public DashboardPeriod calculatePeriod(LocalDate referenceDate) {
        return DashboardPeriod.of(this, referenceDate);
    }

    public static DashboardTimeframe fromParam(String param) {
        if (param == null || param.isBlank()) {
            return THIS_MONTH;
        }
        String clean = param.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return switch (clean) {
            case "LAST_7_DAYS", "7DAYS", "DAYS", "7_DAYS", "LAST7DAYS" -> LAST_7_DAYS;
            case "THIS_MONTH", "MONTH", "THISMONTH" -> THIS_MONTH;
            case "LAST_MONTH", "PREV_MONTH", "LASTMONTH", "PREVIOUS_MONTH" -> LAST_MONTH;
            case "LAST_6_MONTHS", "6MONTHS", "6_MONTHS", "MONTHS", "LAST6MONTHS" -> LAST_6_MONTHS;
            case "THIS_YEAR", "YEAR", "CURRENT_YEAR", "THISYEAR" -> THIS_YEAR;
            default -> {
                try {
                    yield DashboardTimeframe.valueOf(clean);
                } catch (IllegalArgumentException ex) {
                    yield THIS_MONTH;
                }
            }
        };
    }
}

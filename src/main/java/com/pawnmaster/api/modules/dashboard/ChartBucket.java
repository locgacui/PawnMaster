package com.pawnmaster.api.modules.dashboard;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

public record ChartBucket(
        String key,
        String label,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime fromDateTime,
        LocalDateTime toDateTimeExclusive
) {
    public ChartBucket {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(startDate, "startDate must not be null");
        Objects.requireNonNull(endDate, "endDate must not be null");
        Objects.requireNonNull(fromDateTime, "fromDateTime must not be null");
        Objects.requireNonNull(toDateTimeExclusive, "toDateTimeExclusive must not be null");
    }

    public ChartBucket(String key, String label, LocalDate startDate, LocalDate endDate) {
        this(
                key,
                label,
                startDate,
                endDate,
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay()
        );
    }

    public boolean contains(LocalDate date) {
        if (date == null) {
            return false;
        }
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public boolean contains(LocalDateTime dateTime) {
        if (dateTime == null) {
            return false;
        }
        return !dateTime.isBefore(fromDateTime) && dateTime.isBefore(toDateTimeExclusive);
    }
}

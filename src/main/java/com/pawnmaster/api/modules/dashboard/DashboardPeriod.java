package com.pawnmaster.api.modules.dashboard;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DashboardPeriod(
        DashboardTimeframe timeframe,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime fromDateTime,
        LocalDateTime toDateTimeExclusive,
        boolean includeCurrentOverdue,
        String label,
        String rangeLabel,
        List<ChartBucket> buckets
) {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public DashboardPeriod {
        Objects.requireNonNull(timeframe, "timeframe must not be null");
        Objects.requireNonNull(startDate, "startDate must not be null");
        Objects.requireNonNull(endDate, "endDate must not be null");
        Objects.requireNonNull(fromDateTime, "fromDateTime must not be null");
        Objects.requireNonNull(toDateTimeExclusive, "toDateTimeExclusive must not be null");
        buckets = buckets != null ? List.copyOf(buckets) : List.of();
    }

    public static DashboardPeriod of(DashboardTimeframe timeframe) {
        return of(timeframe, LocalDate.now());
    }

    public static DashboardPeriod of(DashboardTimeframe timeframe, LocalDate referenceDate) {
        DashboardTimeframe tf = timeframe != null ? timeframe : DashboardTimeframe.THIS_MONTH;
        LocalDate today = referenceDate != null ? referenceDate : LocalDate.now();

        return switch (tf) {
            case LAST_7_DAYS -> calculateLast7Days(today);
            case THIS_MONTH -> calculateThisMonth(today);
            case LAST_MONTH -> calculateLastMonth(today);
            case LAST_6_MONTHS -> calculateLast6Months(today);
            case THIS_YEAR -> calculateThisYear(today);
        };
    }

    public Optional<ChartBucket> findBucket(LocalDate date) {
        if (date == null || buckets == null) {
            return Optional.empty();
        }
        return buckets.stream().filter(b -> b.contains(date)).findFirst();
    }

    public Optional<ChartBucket> findBucket(LocalDateTime dateTime) {
        if (dateTime == null || buckets == null) {
            return Optional.empty();
        }
        return buckets.stream().filter(b -> b.contains(dateTime)).findFirst();
    }

    private static DashboardPeriod calculateLast7Days(LocalDate today) {
        LocalDate start = today.minusDays(6);
        LocalDate end = today;
        LocalDateTime fromTime = start.atStartOfDay();
        LocalDateTime toTime = end.plusDays(1).atStartOfDay();
        String label = DashboardTimeframe.LAST_7_DAYS.getLabel();
        String rangeLabel = label + " (" + start.format(DATE_FMT) + " \u2014 " + end.format(DATE_FMT) + ")";

        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("dd/MM");
        List<ChartBucket> buckets = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate d = start.plusDays(i);
            buckets.add(new ChartBucket(d.toString(), d.format(dayFmt), d, d));
        }

        return new DashboardPeriod(
                DashboardTimeframe.LAST_7_DAYS,
                start,
                end,
                fromTime,
                toTime,
                true,
                label,
                rangeLabel,
                Collections.unmodifiableList(buckets)
        );
    }

    private static DashboardPeriod calculateThisMonth(LocalDate today) {
        LocalDate start = today.withDayOfMonth(1);
        LocalDate end = today.withDayOfMonth(today.lengthOfMonth());
        LocalDateTime fromTime = start.atStartOfDay();
        LocalDateTime toTime = end.plusDays(1).atStartOfDay();
        String label = DashboardTimeframe.THIS_MONTH.getLabel();
        String rangeLabel = label + " (" + start.format(DATE_FMT) + " \u2014 " + end.format(DATE_FMT) + ")";
        List<ChartBucket> buckets = generateMonthWeeklyBuckets(start, end);

        return new DashboardPeriod(
                DashboardTimeframe.THIS_MONTH,
                start,
                end,
                fromTime,
                toTime,
                true,
                label,
                rangeLabel,
                Collections.unmodifiableList(buckets)
        );
    }

    private static DashboardPeriod calculateLastMonth(LocalDate today) {
        YearMonth lastYm = YearMonth.from(today).minusMonths(1);
        LocalDate start = lastYm.atDay(1);
        LocalDate end = lastYm.atEndOfMonth();
        LocalDateTime fromTime = start.atStartOfDay();
        LocalDateTime toTime = end.plusDays(1).atStartOfDay();
        String label = DashboardTimeframe.LAST_MONTH.getLabel();
        String rangeLabel = label + " (" + start.format(DATE_FMT) + " \u2014 " + end.format(DATE_FMT) + ")";
        List<ChartBucket> buckets = generateMonthWeeklyBuckets(start, end);

        return new DashboardPeriod(
                DashboardTimeframe.LAST_MONTH,
                start,
                end,
                fromTime,
                toTime,
                false, // Closed historical period: do not include current overdue
                label,
                rangeLabel,
                Collections.unmodifiableList(buckets)
        );
    }

    private static List<ChartBucket> generateMonthWeeklyBuckets(LocalDate start, LocalDate end) {
        List<ChartBucket> buckets = new ArrayList<>(5);
        int month = start.getMonthValue();
        int length = end.getDayOfMonth();

        buckets.add(createWeeklyBucket(start, 1, 7, month));
        buckets.add(createWeeklyBucket(start, 8, 14, month));
        buckets.add(createWeeklyBucket(start, 15, 21, month));
        buckets.add(createWeeklyBucket(start, 22, 28, month));

        if (length > 28) {
            LocalDate segStart = start.withDayOfMonth(29);
            LocalDate segEnd = start.withDayOfMonth(length);
            String key = segStart.toString() + "_" + segEnd.toString();
            String bucketLabel = (29 == length)
                    ? String.format("%02d/%02d", 29, month)
                    : String.format("%02d-%02d/%02d", 29, length, month);
            buckets.add(new ChartBucket(key, bucketLabel, segStart, segEnd));
        }
        return buckets;
    }

    private static ChartBucket createWeeklyBucket(LocalDate monthStart, int startDay, int endDay, int month) {
        LocalDate s = monthStart.withDayOfMonth(startDay);
        LocalDate e = monthStart.withDayOfMonth(endDay);
        String key = s.toString() + "_" + e.toString();
        String label = String.format("%02d-%02d/%02d", startDay, endDay, month);
        return new ChartBucket(key, label, s, e);
    }

    private static DashboardPeriod calculateLast6Months(LocalDate today) {
        YearMonth currentYm = YearMonth.from(today);
        YearMonth startYm = currentYm.minusMonths(5);
        LocalDate start = startYm.atDay(1);
        LocalDate end = currentYm.atEndOfMonth();
        LocalDateTime fromTime = start.atStartOfDay();
        LocalDateTime toTime = end.plusDays(1).atStartOfDay();
        String label = DashboardTimeframe.LAST_6_MONTHS.getLabel();
        String rangeLabel = label + " (" + start.format(DATE_FMT) + " \u2014 " + end.format(DATE_FMT) + ")";

        List<ChartBucket> buckets = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) {
            YearMonth ym = startYm.plusMonths(i);
            LocalDate mStart = ym.atDay(1);
            LocalDate mEnd = ym.atEndOfMonth();
            String key = ym.toString();
            String bucketLabel = "Tháng " + ym.getMonthValue() + "/" + ym.getYear();
            buckets.add(new ChartBucket(key, bucketLabel, mStart, mEnd));
        }

        return new DashboardPeriod(
                DashboardTimeframe.LAST_6_MONTHS,
                start,
                end,
                fromTime,
                toTime,
                true,
                label,
                rangeLabel,
                Collections.unmodifiableList(buckets)
        );
    }

    private static DashboardPeriod calculateThisYear(LocalDate today) {
        int year = today.getYear();
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        LocalDateTime fromTime = start.atStartOfDay();
        LocalDateTime toTime = LocalDate.of(year + 1, 1, 1).atStartOfDay();
        String label = DashboardTimeframe.THIS_YEAR.getLabel();
        String rangeLabel = label + " (" + start.format(DATE_FMT) + " \u2014 " + end.format(DATE_FMT) + ")";

        List<ChartBucket> buckets = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(year, m);
            LocalDate mStart = ym.atDay(1);
            LocalDate mEnd = ym.atEndOfMonth();
            String key = ym.toString();
            String bucketLabel = "T" + m;
            buckets.add(new ChartBucket(key, bucketLabel, mStart, mEnd));
        }

        return new DashboardPeriod(
                DashboardTimeframe.THIS_YEAR,
                start,
                end,
                fromTime,
                toTime,
                true,
                label,
                rangeLabel,
                Collections.unmodifiableList(buckets)
        );
    }
}

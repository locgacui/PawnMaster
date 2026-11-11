package com.pawnmaster.api.modules.dashboard;

import com.pawnmaster.api.modules.dashboard.dto.DashboardContractPageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Dashboard Timeframe, Period, Tab & Pagination Unit Tests")
class DashboardTimeframeTest {

    @Nested
    @DisplayName("DashboardTimeframe Enum & Parsing Tests")
    class TimeframeParsingTests {

        @ParameterizedTest(name = "fromParam({0}) should fallback to THIS_MONTH")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "INVALID", "unknown", "2026-11", "foo_bar"})
        void shouldFallbackToThisMonthOnNullEmptyOrInvalid(String param) {
            assertThat(DashboardTimeframe.fromParam(param)).isEqualTo(DashboardTimeframe.THIS_MONTH);
        }

        @Test
        void shouldParseExactEnumNames() {
            assertThat(DashboardTimeframe.fromParam("LAST_7_DAYS")).isEqualTo(DashboardTimeframe.LAST_7_DAYS);
            assertThat(DashboardTimeframe.fromParam("THIS_MONTH")).isEqualTo(DashboardTimeframe.THIS_MONTH);
            assertThat(DashboardTimeframe.fromParam("LAST_MONTH")).isEqualTo(DashboardTimeframe.LAST_MONTH);
            assertThat(DashboardTimeframe.fromParam("LAST_6_MONTHS")).isEqualTo(DashboardTimeframe.LAST_6_MONTHS);
            assertThat(DashboardTimeframe.fromParam("THIS_YEAR")).isEqualTo(DashboardTimeframe.THIS_YEAR);
        }

        @Test
        void shouldHandleCaseInsensitivityAndHyphens() {
            assertThat(DashboardTimeframe.fromParam("last_7_days")).isEqualTo(DashboardTimeframe.LAST_7_DAYS);
            assertThat(DashboardTimeframe.fromParam("last-7-days")).isEqualTo(DashboardTimeframe.LAST_7_DAYS);
            assertThat(DashboardTimeframe.fromParam("this-month")).isEqualTo(DashboardTimeframe.THIS_MONTH);
            assertThat(DashboardTimeframe.fromParam("last-month")).isEqualTo(DashboardTimeframe.LAST_MONTH);
            assertThat(DashboardTimeframe.fromParam("last-6-months")).isEqualTo(DashboardTimeframe.LAST_6_MONTHS);
            assertThat(DashboardTimeframe.fromParam("this-year")).isEqualTo(DashboardTimeframe.THIS_YEAR);
        }

        @Test
        void shouldMapLegacyAndSynonymAliases() {
            assertThat(DashboardTimeframe.fromParam("7days")).isEqualTo(DashboardTimeframe.LAST_7_DAYS);
            assertThat(DashboardTimeframe.fromParam("days")).isEqualTo(DashboardTimeframe.LAST_7_DAYS);
            assertThat(DashboardTimeframe.fromParam("month")).isEqualTo(DashboardTimeframe.THIS_MONTH);
            assertThat(DashboardTimeframe.fromParam("prev_month")).isEqualTo(DashboardTimeframe.LAST_MONTH);
            assertThat(DashboardTimeframe.fromParam("6months")).isEqualTo(DashboardTimeframe.LAST_6_MONTHS);
            assertThat(DashboardTimeframe.fromParam("months")).isEqualTo(DashboardTimeframe.LAST_6_MONTHS);
            assertThat(DashboardTimeframe.fromParam("year")).isEqualTo(DashboardTimeframe.THIS_YEAR);
            assertThat(DashboardTimeframe.fromParam("current_year")).isEqualTo(DashboardTimeframe.THIS_YEAR);
        }

        @Test
        void shouldReturnCorrectLabelsAndLegacyKeys() {
            assertThat(DashboardTimeframe.LAST_7_DAYS.getLabel()).isEqualTo("7 ngày qua");
            assertThat(DashboardTimeframe.LAST_7_DAYS.getLegacyChartKey()).isEqualTo("days");
            assertThat(DashboardTimeframe.LAST_7_DAYS.getCode()).isEqualTo("LAST_7_DAYS");

            assertThat(DashboardTimeframe.THIS_MONTH.getLabel()).isEqualTo("Tháng này");
            assertThat(DashboardTimeframe.THIS_MONTH.getLegacyChartKey()).isEqualTo("months");

            assertThat(DashboardTimeframe.LAST_MONTH.getLabel()).isEqualTo("Tháng trước");
            assertThat(DashboardTimeframe.LAST_MONTH.getLegacyChartKey()).isEqualTo("months");

            assertThat(DashboardTimeframe.LAST_6_MONTHS.getLabel()).isEqualTo("6 tháng trước");
            assertThat(DashboardTimeframe.LAST_6_MONTHS.getLegacyChartKey()).isEqualTo("months");

            assertThat(DashboardTimeframe.THIS_YEAR.getLabel()).isEqualTo("Năm hiện tại");
            assertThat(DashboardTimeframe.THIS_YEAR.getLegacyChartKey()).isEqualTo("year");
        }
    }

    @Nested
    @DisplayName("DashboardPeriod Calculation & Boundary Tests")
    class PeriodCalculationTests {

        private final LocalDate standardRefDate = LocalDate.of(2026, 11, 11);

        @Test
        void shouldCalculateLast7DaysCorrectly() {
            DashboardPeriod period = DashboardTimeframe.LAST_7_DAYS.calculatePeriod(standardRefDate);

            assertThat(period.timeframe()).isEqualTo(DashboardTimeframe.LAST_7_DAYS);
            assertThat(period.startDate()).isEqualTo(LocalDate.of(2026, 11, 5));
            assertThat(period.endDate()).isEqualTo(LocalDate.of(2026, 11, 11));
            assertThat(period.fromDateTime()).isEqualTo(LocalDateTime.of(2026, 11, 5, 0, 0, 0));
            assertThat(period.toDateTimeExclusive()).isEqualTo(LocalDateTime.of(2026, 11, 12, 0, 0, 0));
            assertThat(period.includeCurrentOverdue()).isTrue();
            assertThat(period.label()).isEqualTo("7 ngày qua");
            assertThat(period.rangeLabel()).isEqualTo("7 ngày qua (05/11/2026 \u2014 11/11/2026)");

            List<ChartBucket> buckets = period.buckets();
            assertThat(buckets).hasSize(7);
            assertThat(buckets.get(0).label()).isEqualTo("05/11");
            assertThat(buckets.get(0).startDate()).isEqualTo(LocalDate.of(2026, 11, 5));
            assertThat(buckets.get(6).label()).isEqualTo("11/11");
            assertThat(buckets.get(6).endDate()).isEqualTo(LocalDate.of(2026, 11, 11));
        }

        @Test
        void shouldCalculateThisMonthCorrectlyFor30DayMonth() {
            DashboardPeriod period = DashboardTimeframe.THIS_MONTH.calculatePeriod(standardRefDate);

            assertThat(period.timeframe()).isEqualTo(DashboardTimeframe.THIS_MONTH);
            assertThat(period.startDate()).isEqualTo(LocalDate.of(2026, 11, 1));
            assertThat(period.endDate()).isEqualTo(LocalDate.of(2026, 11, 30));
            assertThat(period.fromDateTime()).isEqualTo(LocalDateTime.of(2026, 11, 1, 0, 0, 0));
            assertThat(period.toDateTimeExclusive()).isEqualTo(LocalDateTime.of(2026, 12, 1, 0, 0, 0));
            assertThat(period.includeCurrentOverdue()).isTrue();
            assertThat(period.label()).isEqualTo("Tháng này");
            assertThat(period.rangeLabel()).isEqualTo("Tháng này (01/11/2026 \u2014 30/11/2026)");

            List<ChartBucket> buckets = period.buckets();
            assertThat(buckets).hasSize(5);
            assertThat(buckets.get(0).label()).isEqualTo("01-07/11");
            assertThat(buckets.get(1).label()).isEqualTo("08-14/11");
            assertThat(buckets.get(2).label()).isEqualTo("15-21/11");
            assertThat(buckets.get(3).label()).isEqualTo("22-28/11");
            assertThat(buckets.get(4).label()).isEqualTo("29-30/11");
            assertThat(buckets.get(4).startDate()).isEqualTo(LocalDate.of(2026, 11, 29));
            assertThat(buckets.get(4).endDate()).isEqualTo(LocalDate.of(2026, 11, 30));
        }

        @Test
        void shouldCalculateLastMonthCorrectlyAndExcludeCurrentOverdue() {
            DashboardPeriod period = DashboardTimeframe.LAST_MONTH.calculatePeriod(standardRefDate);

            assertThat(period.timeframe()).isEqualTo(DashboardTimeframe.LAST_MONTH);
            assertThat(period.startDate()).isEqualTo(LocalDate.of(2026, 10, 1));
            assertThat(period.endDate()).isEqualTo(LocalDate.of(2026, 10, 31));
            assertThat(period.fromDateTime()).isEqualTo(LocalDateTime.of(2026, 10, 1, 0, 0, 0));
            assertThat(period.toDateTimeExclusive()).isEqualTo(LocalDateTime.of(2026, 11, 1, 0, 0, 0));
            assertThat(period.includeCurrentOverdue()).isFalse(); // Closed historical period
            assertThat(period.label()).isEqualTo("Tháng trước");
            assertThat(period.rangeLabel()).isEqualTo("Tháng trước (01/10/2026 \u2014 31/10/2026)");

            List<ChartBucket> buckets = period.buckets();
            assertThat(buckets).hasSize(5);
            assertThat(buckets.get(0).label()).isEqualTo("01-07/10");
            assertThat(buckets.get(4).label()).isEqualTo("29-31/10");
            assertThat(buckets.get(4).endDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        }

        @Test
        void shouldCalculateLast6MonthsCorrectly() {
            DashboardPeriod period = DashboardTimeframe.LAST_6_MONTHS.calculatePeriod(standardRefDate);

            assertThat(period.timeframe()).isEqualTo(DashboardTimeframe.LAST_6_MONTHS);
            assertThat(period.startDate()).isEqualTo(LocalDate.of(2026, 6, 1));
            assertThat(period.endDate()).isEqualTo(LocalDate.of(2026, 11, 30));
            assertThat(period.fromDateTime()).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0, 0));
            assertThat(period.toDateTimeExclusive()).isEqualTo(LocalDateTime.of(2026, 12, 1, 0, 0, 0));
            assertThat(period.includeCurrentOverdue()).isTrue();
            assertThat(period.label()).isEqualTo("6 tháng trước");
            assertThat(period.rangeLabel()).isEqualTo("6 tháng trước (01/06/2026 \u2014 30/11/2026)");

            List<ChartBucket> buckets = period.buckets();
            assertThat(buckets).hasSize(6);
            assertThat(buckets.get(0).label()).isEqualTo("Tháng 6/2026");
            assertThat(buckets.get(0).startDate()).isEqualTo(LocalDate.of(2026, 6, 1));
            assertThat(buckets.get(5).label()).isEqualTo("Tháng 11/2026");
            assertThat(buckets.get(5).endDate()).isEqualTo(LocalDate.of(2026, 11, 30));
        }

        @Test
        void shouldCalculateThisYearCorrectly() {
            DashboardPeriod period = DashboardTimeframe.THIS_YEAR.calculatePeriod(standardRefDate);

            assertThat(period.timeframe()).isEqualTo(DashboardTimeframe.THIS_YEAR);
            assertThat(period.startDate()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(period.endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
            assertThat(period.fromDateTime()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0, 0));
            assertThat(period.toDateTimeExclusive()).isEqualTo(LocalDateTime.of(2027, 1, 1, 0, 0, 0));
            assertThat(period.includeCurrentOverdue()).isTrue();
            assertThat(period.label()).isEqualTo("Năm hiện tại");
            assertThat(period.rangeLabel()).isEqualTo("Năm hiện tại (01/01/2026 \u2014 31/12/2026)");

            List<ChartBucket> buckets = period.buckets();
            assertThat(buckets).hasSize(12);
            assertThat(buckets.get(0).label()).isEqualTo("T1");
            assertThat(buckets.get(0).startDate()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(buckets.get(11).label()).isEqualTo("T12");
            assertThat(buckets.get(11).endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        }

        @Test
        void shouldHandleLeapYearFebruary29Correctly() {
            LocalDate leapDate = LocalDate.of(2024, 2, 29);
            DashboardPeriod period = DashboardTimeframe.THIS_MONTH.calculatePeriod(leapDate);

            assertThat(period.startDate()).isEqualTo(LocalDate.of(2024, 2, 1));
            assertThat(period.endDate()).isEqualTo(LocalDate.of(2024, 2, 29));
            assertThat(period.toDateTimeExclusive()).isEqualTo(LocalDateTime.of(2024, 3, 1, 0, 0, 0));

            List<ChartBucket> buckets = period.buckets();
            assertThat(buckets).hasSize(5);
            assertThat(buckets.get(0).label()).isEqualTo("01-07/02");
            assertThat(buckets.get(1).label()).isEqualTo("08-14/02");
            assertThat(buckets.get(2).label()).isEqualTo("15-21/02");
            assertThat(buckets.get(3).label()).isEqualTo("22-28/02");
            assertThat(buckets.get(4).label()).isEqualTo("29/02");
            assertThat(buckets.get(4).startDate()).isEqualTo(LocalDate.of(2024, 2, 29));
            assertThat(buckets.get(4).endDate()).isEqualTo(LocalDate.of(2024, 2, 29));
        }

        @Test
        void shouldHandleNonLeapYearFebruary28WithExactlyFourBuckets() {
            LocalDate nonLeapDate = LocalDate.of(2025, 2, 15);
            DashboardPeriod period = DashboardTimeframe.THIS_MONTH.calculatePeriod(nonLeapDate);

            assertThat(period.startDate()).isEqualTo(LocalDate.of(2025, 2, 1));
            assertThat(period.endDate()).isEqualTo(LocalDate.of(2025, 2, 28));
            assertThat(period.toDateTimeExclusive()).isEqualTo(LocalDateTime.of(2025, 3, 1, 0, 0, 0));

            List<ChartBucket> buckets = period.buckets();
            assertThat(buckets).hasSize(4);
            assertThat(buckets.get(0).label()).isEqualTo("01-07/02");
            assertThat(buckets.get(3).label()).isEqualTo("22-28/02");
            assertThat(buckets.get(3).endDate()).isEqualTo(LocalDate.of(2025, 2, 28));
        }

        @Test
        void shouldHandleYearRolloverInJanuary() {
            LocalDate jan3 = LocalDate.of(2026, 1, 3);

            // LAST_7_DAYS spanning Dec 2025 to Jan 2026
            DashboardPeriod last7Days = DashboardTimeframe.LAST_7_DAYS.calculatePeriod(jan3);
            assertThat(last7Days.startDate()).isEqualTo(LocalDate.of(2025, 12, 28));
            assertThat(last7Days.endDate()).isEqualTo(LocalDate.of(2026, 1, 3));
            assertThat(last7Days.buckets()).hasSize(7);
            assertThat(last7Days.buckets().get(0).label()).isEqualTo("28/12");
            assertThat(last7Days.buckets().get(6).label()).isEqualTo("03/01");

            // LAST_MONTH referring to Dec 2025
            DashboardPeriod lastMonth = DashboardTimeframe.LAST_MONTH.calculatePeriod(jan3);
            assertThat(lastMonth.startDate()).isEqualTo(LocalDate.of(2025, 12, 1));
            assertThat(lastMonth.endDate()).isEqualTo(LocalDate.of(2025, 12, 31));
            assertThat(lastMonth.toDateTimeExclusive()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0, 0));
            assertThat(lastMonth.rangeLabel()).contains("01/12/2025 \u2014 31/12/2025");

            // LAST_6_MONTHS spanning Aug 2025 to Jan 2026
            DashboardPeriod last6Months = DashboardTimeframe.LAST_6_MONTHS.calculatePeriod(jan3);
            assertThat(last6Months.startDate()).isEqualTo(LocalDate.of(2025, 8, 1));
            assertThat(last6Months.endDate()).isEqualTo(LocalDate.of(2026, 1, 31));
            assertThat(last6Months.buckets()).hasSize(6);
            assertThat(last6Months.buckets().get(0).label()).isEqualTo("Tháng 8/2025");
            assertThat(last6Months.buckets().get(5).label()).isEqualTo("Tháng 1/2026");
        }

        @Test
        void shouldHandleYearEndDecember31() {
            LocalDate dec31 = LocalDate.of(2026, 12, 31);
            DashboardPeriod period = DashboardTimeframe.THIS_YEAR.calculatePeriod(dec31);

            assertThat(period.startDate()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(period.endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
            assertThat(period.toDateTimeExclusive()).isEqualTo(LocalDateTime.of(2027, 1, 1, 0, 0, 0));
            assertThat(period.buckets()).hasSize(12);
        }

        @Test
        void shouldProvideDefaultCalculatePeriod() {
            DashboardPeriod period = DashboardTimeframe.THIS_MONTH.calculatePeriod();
            assertThat(period).isNotNull();
            assertThat(period.timeframe()).isEqualTo(DashboardTimeframe.THIS_MONTH);
            assertThat(period.buckets()).isNotEmpty();
        }

        @Test
        void shouldFindBucketForGivenDateAndDateTime() {
            DashboardPeriod period = DashboardTimeframe.THIS_MONTH.calculatePeriod(standardRefDate);

            // Date: Nov 10 should fall into second bucket (08-14/11)
            LocalDate testDate = LocalDate.of(2026, 11, 10);
            assertThat(period.findBucket(testDate)).isPresent();
            assertThat(period.findBucket(testDate).get().label()).isEqualTo("08-14/11");

            // DateTime: Nov 10 14:30 should fall into second bucket
            LocalDateTime testDateTime = LocalDateTime.of(2026, 11, 10, 14, 30);
            assertThat(period.findBucket(testDateTime)).isPresent();
            assertThat(period.findBucket(testDateTime).get().label()).isEqualTo("08-14/11");

            // Null inputs should return empty
            assertThat(period.findBucket((LocalDate) null)).isEmpty();
            assertThat(period.findBucket((LocalDateTime) null)).isEmpty();

            // Date outside period
            assertThat(period.findBucket(LocalDate.of(2025, 1, 1))).isEmpty();
        }

        @Test
        void shouldEnforcePeriodImmutabilityAndNotNullConstraints() {
            assertThatThrownBy(() -> new DashboardPeriod(
                    null,
                    LocalDate.now(),
                    LocalDate.now(),
                    LocalDateTime.now(),
                    LocalDateTime.now().plusDays(1),
                    true,
                    "label",
                    "rangeLabel",
                    Collections.emptyList()
            )).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("timeframe");
        }
    }

    @Nested
    @DisplayName("ChartBucket Unit Tests")
    class ChartBucketTests {

        private final ChartBucket bucket = new ChartBucket(
                "2026-11-08_2026-11-14",
                "08-14/11",
                LocalDate.of(2026, 11, 8),
                LocalDate.of(2026, 11, 14)
        );

        @Test
        void shouldVerifyAutoGeneratedDateTimeBoundaries() {
            assertThat(bucket.fromDateTime()).isEqualTo(LocalDateTime.of(2026, 11, 8, 0, 0, 0));
            assertThat(bucket.toDateTimeExclusive()).isEqualTo(LocalDateTime.of(2026, 11, 15, 0, 0, 0));
        }

        @Test
        void shouldVerifyContainsLocalDate() {
            assertThat(bucket.contains((LocalDate) null)).isFalse();
            assertThat(bucket.contains(LocalDate.of(2026, 11, 7))).isFalse();
            assertThat(bucket.contains(LocalDate.of(2026, 11, 8))).isTrue(); // start boundary
            assertThat(bucket.contains(LocalDate.of(2026, 11, 11))).isTrue(); // middle
            assertThat(bucket.contains(LocalDate.of(2026, 11, 14))).isTrue(); // end boundary
            assertThat(bucket.contains(LocalDate.of(2026, 11, 15))).isFalse();
        }

        @Test
        void shouldVerifyContainsLocalDateTimeHalfOpen() {
            assertThat(bucket.contains((LocalDateTime) null)).isFalse();
            // Before
            assertThat(bucket.contains(LocalDateTime.of(2026, 11, 7, 23, 59, 59))).isFalse();
            // Exact start 00:00:00 -> INCLUDED
            assertThat(bucket.contains(LocalDateTime.of(2026, 11, 8, 0, 0, 0))).isTrue();
            // Middle
            assertThat(bucket.contains(LocalDateTime.of(2026, 11, 11, 12, 0, 0))).isTrue();
            // End of Nov 14 (23:59:59.999999999) -> INCLUDED
            assertThat(bucket.contains(LocalDateTime.of(2026, 11, 14, 23, 59, 59, 999_999_999))).isTrue();
            // Exact start of Nov 15 00:00:00 -> EXCLUDED (half-open)
            assertThat(bucket.contains(LocalDateTime.of(2026, 11, 15, 0, 0, 0))).isFalse();
            // After
            assertThat(bucket.contains(LocalDateTime.of(2026, 11, 15, 0, 0, 1))).isFalse();
        }

        @Test
        void shouldRejectNullConstructorArgs() {
            assertThatThrownBy(() -> new ChartBucket(null, "label", LocalDate.now(), LocalDate.now()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ChartBucket("key", null, LocalDate.now(), LocalDate.now()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ChartBucket("key", "label", null, LocalDate.now()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ChartBucket("key", "label", LocalDate.now(), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("DashboardContractTab Enum & Parsing Tests")
    class ContractTabTests {

        @ParameterizedTest(name = "fromParam({0}) should fallback to ALL")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "INVALID", "unknown", "foo"})
        void shouldFallbackToAllOnNullEmptyOrInvalid(String param) {
            assertThat(DashboardContractTab.fromParam(param)).isEqualTo(DashboardContractTab.ALL);
        }

        @Test
        void shouldParseExactTabs() {
            assertThat(DashboardContractTab.fromParam("ALL")).isEqualTo(DashboardContractTab.ALL);
            assertThat(DashboardContractTab.fromParam("PENDING_APPROVAL")).isEqualTo(DashboardContractTab.PENDING_APPROVAL);
            assertThat(DashboardContractTab.fromParam("ACTIVE")).isEqualTo(DashboardContractTab.ACTIVE);
            assertThat(DashboardContractTab.fromParam("DUE_TODAY")).isEqualTo(DashboardContractTab.DUE_TODAY);
            assertThat(DashboardContractTab.fromParam("OVERDUE")).isEqualTo(DashboardContractTab.OVERDUE);
            assertThat(DashboardContractTab.fromParam("WAITING_LIQUIDATION")).isEqualTo(DashboardContractTab.WAITING_LIQUIDATION);
        }

        @Test
        void shouldHandleCaseAndHyphenVariations() {
            assertThat(DashboardContractTab.fromParam("pending-approval")).isEqualTo(DashboardContractTab.PENDING_APPROVAL);
            assertThat(DashboardContractTab.fromParam("due-today")).isEqualTo(DashboardContractTab.DUE_TODAY);
            assertThat(DashboardContractTab.fromParam("waiting-liquidation")).isEqualTo(DashboardContractTab.WAITING_LIQUIDATION);
            assertThat(DashboardContractTab.fromParam("active")).isEqualTo(DashboardContractTab.ACTIVE);
            assertThat(DashboardContractTab.fromParam("overdue")).isEqualTo(DashboardContractTab.OVERDUE);
        }

        @Test
        void shouldMapSynonymAliases() {
            assertThat(DashboardContractTab.fromParam("pending")).isEqualTo(DashboardContractTab.PENDING_APPROVAL);
            assertThat(DashboardContractTab.fromParam("liquidation")).isEqualTo(DashboardContractTab.WAITING_LIQUIDATION);
            assertThat(DashboardContractTab.fromParam("liquidating")).isEqualTo(DashboardContractTab.WAITING_LIQUIDATION);
        }

        @Test
        void shouldReturnCorrectLabels() {
            assertThat(DashboardContractTab.ALL.getLabel()).isEqualTo("Tất cả");
            assertThat(DashboardContractTab.PENDING_APPROVAL.getLabel()).isEqualTo("Chờ duyệt");
            assertThat(DashboardContractTab.ACTIVE.getLabel()).isEqualTo("Đang vay");
            assertThat(DashboardContractTab.DUE_TODAY.getLabel()).isEqualTo("Đến hạn hôm nay");
            assertThat(DashboardContractTab.OVERDUE.getLabel()).isEqualTo("Quá hạn");
            assertThat(DashboardContractTab.WAITING_LIQUIDATION.getLabel()).isEqualTo("Chờ thanh lý");
            assertThat(DashboardContractTab.ALL.getCode()).isEqualTo("ALL");
        }
    }

    @Nested
    @DisplayName("DashboardContractPageResponse DTO Tests")
    class ContractPageResponseTests {

        @Test
        void shouldCalculatePagesAndNavigationCorrectlyForFirstPage() {
            DashboardContractPageResponse resp = DashboardContractPageResponse.of(
                    Collections.emptyList(),
                    0,
                    10,
                    25L,
                    "ACTIVE",
                    "HD001"
            );

            assertThat(resp.getPage()).isEqualTo(0);
            assertThat(resp.getDisplayPage()).isEqualTo(1);
            assertThat(resp.getSize()).isEqualTo(10);
            assertThat(resp.getTotalElements()).isEqualTo(25L);
            assertThat(resp.getTotalPages()).isEqualTo(3);
            assertThat(resp.isHasNext()).isTrue();
            assertThat(resp.isHasPrevious()).isFalse();
            assertThat(resp.getActiveTab()).isEqualTo("ACTIVE");
            assertThat(resp.getKeyword()).isEqualTo("HD001");
        }

        @Test
        void shouldCalculatePagesAndNavigationCorrectlyForMiddlePage() {
            DashboardContractPageResponse resp = DashboardContractPageResponse.of(
                    Collections.emptyList(),
                    1,
                    10,
                    25L,
                    "ALL",
                    null
            );

            assertThat(resp.getPage()).isEqualTo(1);
            assertThat(resp.getDisplayPage()).isEqualTo(2);
            assertThat(resp.isHasNext()).isTrue();
            assertThat(resp.isHasPrevious()).isTrue();
        }

        @Test
        void shouldCalculatePagesAndNavigationCorrectlyForLastPage() {
            DashboardContractPageResponse resp = DashboardContractPageResponse.of(
                    Collections.emptyList(),
                    2,
                    10,
                    25L,
                    "ALL",
                    null
            );

            assertThat(resp.getPage()).isEqualTo(2);
            assertThat(resp.getDisplayPage()).isEqualTo(3);
            assertThat(resp.isHasNext()).isFalse();
            assertThat(resp.isHasPrevious()).isTrue();
        }

        @Test
        void shouldHandleZeroTotalElements() {
            DashboardContractPageResponse resp = DashboardContractPageResponse.of(
                    Collections.emptyList(),
                    0,
                    10,
                    0L,
                    null,
                    null
            );

            assertThat(resp.getTotalElements()).isEqualTo(0L);
            assertThat(resp.getTotalPages()).isEqualTo(0);
            assertThat(resp.isHasNext()).isFalse();
            assertThat(resp.isHasPrevious()).isFalse();
            assertThat(resp.getActiveTab()).isEqualTo("ALL");
        }

        @ParameterizedTest(name = "size {0} should clamp to {1}")
        @CsvSource({
                "-10, 10",
                "0, 10",
                "1, 1",
                "10, 10",
                "50, 50",
                "100, 50",
                "1000, 50"
        })
        void shouldClampPageSizeBetween1And50(int rawSize, int expectedSize) {
            DashboardContractPageResponse resp = DashboardContractPageResponse.of(
                    Collections.emptyList(),
                    0,
                    rawSize,
                    100L,
                    "ALL",
                    null
            );
            assertThat(resp.getSize()).isEqualTo(expectedSize);
        }

        @Test
        void shouldClampNegativePageToZero() {
            DashboardContractPageResponse resp = DashboardContractPageResponse.of(
                    Collections.emptyList(),
                    -5,
                    10,
                    50L,
                    "ALL",
                    null
            );
            assertThat(resp.getPage()).isEqualTo(0);
            assertThat(resp.getDisplayPage()).isEqualTo(1);
        }

        @Test
        void shouldCreateEmptyPage() {
            DashboardContractPageResponse resp = DashboardContractPageResponse.empty(10, "OVERDUE", "search");
            assertThat(resp.getItems()).isEmpty();
            assertThat(resp.getPage()).isEqualTo(0);
            assertThat(resp.getDisplayPage()).isEqualTo(1);
            assertThat(resp.getTotalElements()).isEqualTo(0L);
            assertThat(resp.getTotalPages()).isEqualTo(0);
            assertThat(resp.isHasNext()).isFalse();
            assertThat(resp.isHasPrevious()).isFalse();
            assertThat(resp.getActiveTab()).isEqualTo("OVERDUE");
            assertThat(resp.getKeyword()).isEqualTo("search");
        }
    }
}

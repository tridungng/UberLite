package com.uberlite.forecasting.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The rolling-average rule, tested without a database or a broker.
 *
 * <p>This is the whole of the MVP's "forecast", so the edge cases below are the specification:
 * what happens on days with no data, and which days are allowed to count at all.
 */
class ForecastWindowTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    void averagesOverTheFullWindowNotOverTheDaysThatHaveData() {
        ForecastWindow window = new ForecastWindow(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7), 7);

        // 21 requests spread over three days out of seven.
        assertThat(window.average(List.of(10L, 7L, 4L))).isEqualTo(3.0);
    }

    @Test
    void treatsDaysWithNoRowsAsZeroDemandRatherThanAsMissingData() {
        ForecastWindow window = new ForecastWindow(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7), 7);

        // A cell that saw 70 requests on one single day is not as busy as one that saw 70 every
        // day; dividing by the number of rows found would make them indistinguishable.
        assertThat(window.average(List.of(70L))).isEqualTo(10.0);
        assertThat(window.average(List.of(10L, 10L, 10L, 10L, 10L, 10L, 10L))).isEqualTo(10.0);
    }

    @Test
    void aCellWithNoHistoryForecastsZero() {
        ForecastWindow window = new ForecastWindow(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7), 7);

        assertThat(window.average(List.of())).isZero();
    }

    @Test
    void windowEndsYesterdayWhenTodaysHourHasNotFinishedYet() {
        // 14:00 on the 10th, asking about the 20:00 hour: today's 20:00 bucket is empty because the
        // evening has not happened, not because demand vanished.
        Instant now = Instant.parse("2026-08-10T14:00:00Z");

        ForecastWindow window = ForecastWindow.endingAtLastCompleteBucket(now, UTC, 20, 7);

        assertThat(window.to()).isEqualTo(LocalDate.of(2026, 8, 9));
        assertThat(window.from()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(window.lengthInDays()).isEqualTo(7);
    }

    @Test
    void windowIncludesTodayOnceTheHourHasFullyElapsed() {
        // 14:00 on the 10th, asking about the 09:00 hour: that hour finished at 10:00 today, so
        // today's bucket is complete and excluding it would throw away the freshest data point.
        Instant now = Instant.parse("2026-08-10T14:00:00Z");

        ForecastWindow window = ForecastWindow.endingAtLastCompleteBucket(now, UTC, 9, 7);

        assertThat(window.to()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(window.from()).isEqualTo(LocalDate.of(2026, 8, 4));
    }

    @Test
    void theCurrentHourIsNotCompleteUntilItEnds() {
        // 14:30, asking about the 14:00 hour: half the hour's demand has not arrived yet, so
        // including it would report a dip that is really just the clock.
        ForecastWindow window = ForecastWindow.endingAtLastCompleteBucket(
                Instant.parse("2026-08-10T14:30:00Z"), UTC, 14, 7);
        assertThat(window.to()).isEqualTo(LocalDate.of(2026, 8, 9));

        // At 15:00 exactly, the 14:00 hour is done.
        ForecastWindow afterTheHour = ForecastWindow.endingAtLastCompleteBucket(
                Instant.parse("2026-08-10T15:00:00Z"), UTC, 14, 7);
        assertThat(afterTheHour.to()).isEqualTo(LocalDate.of(2026, 8, 10));
    }

    @Test
    void resolvesBucketsInTheConfiguredZoneNotUtc() {
        // 01:00 UTC on the 10th is 21:00 on the 9th in New York. A UTC-based bucket would file this
        // evening's demand under the wrong day and the wrong hour entirely.
        Instant now = Instant.parse("2026-08-10T01:00:00Z");

        ForecastWindow window = ForecastWindow.endingAtLastCompleteBucket(
                now, ZoneId.of("America/New_York"), 20, 7);

        assertThat(window.to()).isEqualTo(LocalDate.of(2026, 8, 9));
    }

    @Test
    void rejectsAnEmptyWindow() {
        assertThatThrownBy(() -> new ForecastWindow(LocalDate.now(), LocalDate.now(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}


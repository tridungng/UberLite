package com.uberlite.forecasting.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;

/**
 * The inclusive range of {@code day_bucket}s a forecast averages over, and the arithmetic that
 * turns those buckets into a predicted demand.
 *
 * <p>Pure by design — no repository, no clock lookup — so the rule the paper leaves as "rolling
 * average of the last N days" (Sec. 5) is testable without a database.
 *
 * <h2>Why only <em>complete</em> buckets</h2>
 *
 * <p>The window ends at the most recent day whose {@code hourOfDay} has fully elapsed. Asking at
 * 14:00 for {@code hourOfDay=20} must not average in today's bucket: it reads zero because the
 * evening has not happened yet, not because nobody wants a ride, and including it would drag every
 * forward-looking forecast down by {@code 1/N}.
 *
 * <h2>Why missing buckets count as zero</h2>
 *
 * <p>A day with no row is a day with no trip requests. Dividing by the number of rows found instead
 * of by the window length would make a cell that saw 10 requests on one day out of seven look
 * exactly as busy as one that saw 10 every day.
 */
public record ForecastWindow(LocalDate from, LocalDate to, int days) {

    public ForecastWindow {
        if (days < 1) {
            throw new IllegalArgumentException("Forecast window must span at least one day");
        }
    }

    /**
     * The {@code days}-long window of complete buckets ending at or before {@code now}.
     *
     * @param hourOfDay the hour being forecast, which decides when "today" becomes complete
     */
    public static ForecastWindow endingAtLastCompleteBucket(Instant now, ZoneId zone, int hourOfDay, int days) {
        LocalDateTime localNow = LocalDateTime.ofInstant(now, zone);
        LocalDate today = localNow.toLocalDate();
        // Today's bucket is complete only once the hour it covers has finished.
        boolean todayComplete = !localNow.isBefore(today.atTime(hourOfDay, 0).plusHours(1));
        LocalDate to = todayComplete ? today : today.minusDays(1);
        return new ForecastWindow(to.minusDays(days - 1L), to, days);
    }

    /**
     * Mean demand per day over the window.
     *
     * @param bucketCounts the counts actually recorded inside the window, in any order; absent days
     *     are simply not present and contribute zero
     */
    public double average(Collection<Long> bucketCounts) {
        long total = bucketCounts.stream().mapToLong(Long::longValue).sum();
        return (double) total / days;
    }

    /** @return whether {@code day} falls inside this window. */
    public boolean contains(LocalDate day) {
        return !day.isBefore(from) && !day.isAfter(to);
    }

    /** @return the window length, for callers that want to assert it matches the configured N. */
    public long lengthInDays() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }
}


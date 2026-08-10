package com.uberlite.forecasting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/**
 * Tuning for the rolling-average forecast.
 *
 * @param windowDays how many complete day buckets a forecast averages over. The paper says
 *     "last N days" without fixing N; seven keeps a full weekly cycle so a Saturday forecast is not
 *     dominated by weekdays.
 * @param zone the time zone that defines a "day bucket" and an "hour of day". Demand is a
 *     wall-clock phenomenon — the evening rush is 18:00 locally, not 18:00 UTC — but the MVP is
 *     single-region (ARCHITECTURE.md Sec. 9), so one configured zone is enough. Stored explicitly
 *     rather than taken from the JVM default so a container's time zone cannot silently reshard
 *     every bucket.
 */
@ConfigurationProperties(prefix = "forecasting")
public record ForecastingProperties(int windowDays, ZoneId zone) {

    public ForecastingProperties {
        if (windowDays < 1) {
            throw new IllegalArgumentException("forecasting.window-days must be >= 1");
        }
        if (zone == null) {
            zone = ZoneId.of("UTC");
        }
    }
}


package com.uberlite.discountsanalytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tuning for the nightly promo batch.
 *
 * @param tripThreshold riders with strictly fewer completed trips than this are flagged. Mirrors
 *     the "first 3 rides" rule Discounts &amp; Promotions already seeds (ARCHITECTURE.md Sec. 2),
 *     but kept configurable here because the two are only coincidentally equal today — this batch
 *     decides <em>who to consider</em>, the evaluator decides <em>what they get</em>.
 * @param cron when the batch runs. Externalised so a demo can trigger it without waiting for 02:00.
 */
@ConfigurationProperties(prefix = "discounts-analytics")
public record PromoBatchProperties(int tripThreshold, String cron) {

    public PromoBatchProperties {
        if (tripThreshold < 1) {
            throw new IllegalArgumentException(
                    "discounts-analytics.trip-threshold must be >= 1; a threshold of 0 can never flag anyone");
        }
    }
}


package com.uberlite.apigateway.health;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Tuning for {@link AggregateHealthController}.
 *
 * @param timeout per-instance budget for the fan-out; a service that does not answer within it is
 *                reported {@code DOWN} rather than allowed to stall the whole report
 */
@ConfigurationProperties(prefix = "uberlite.health.aggregate")
public record AggregateHealthProperties(Duration timeout) {

    public AggregateHealthProperties {
        if (timeout == null) {
            timeout = Duration.ofSeconds(2);
        }
    }
}


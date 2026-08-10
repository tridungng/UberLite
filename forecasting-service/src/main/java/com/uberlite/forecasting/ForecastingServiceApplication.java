package com.uberlite.forecasting;

import com.uberlite.common.events.kafka.TripEventConsumerConfiguration;
import com.uberlite.forecasting.config.ForecastingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Clock;

@SpringBootApplication
@EnableConfigurationProperties(ForecastingProperties.class)
@Import(TripEventConsumerConfiguration.class)
public class ForecastingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForecastingServiceApplication.class, args);
    }

    /**
     * Injected rather than called statically so tests can wind the clock forward across day
     * buckets — otherwise the rolling-average window could only ever be exercised against
     * whatever today happens to be.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

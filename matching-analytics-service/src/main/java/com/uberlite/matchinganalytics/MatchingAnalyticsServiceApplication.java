package com.uberlite.matchinganalytics;

import com.uberlite.common.events.kafka.TripEventConsumerConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(TripEventConsumerConfiguration.class)
public class MatchingAnalyticsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MatchingAnalyticsServiceApplication.class, args);
    }
}

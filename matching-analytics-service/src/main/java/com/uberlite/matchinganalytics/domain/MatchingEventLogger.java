package com.uberlite.matchinganalytics.domain;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class MatchingEventLogger {

    @KafkaListener(topics = "trip-events", groupId = "matching-analytics-group")
    public void logMatchingEvent(String message) {
        // Placeholder: In production, this would:
        // 1. Parse the trip event (from DRIVER_PROPOSED state transitions)
        // 2. Log the match input and outcome to Postgres
        // 3. Track success rate, latency, and other metrics
        System.out.println("Matching Analytics Service received trip event: " + message);
    }
}

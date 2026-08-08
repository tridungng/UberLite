package com.uberlite.discountsanalytics.domain;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PromoFlagger {

    @KafkaListener(topics = "trip-events", groupId = "discounts-analytics-group")
    public void processTripsForPromo(String message) {
        // Placeholder: Track ride counts per rider from trip-events
        System.out.println("Discounts Analytics Service received trip event: " + message);
    }

    @Scheduled(cron = "0 0 2 * * *") // Run daily at 2 AM
    public void flagRidersForPromo() {
        // Placeholder: Nightly batch job that:
        // 1. Queries riders with ride_count < 3
        // 2. Flags them for a promo in the discounts-promotions-service
        // 3. Logs the flagging decision
        System.out.println("Discounts Analytics: Running nightly promo flag batch job");
    }
}

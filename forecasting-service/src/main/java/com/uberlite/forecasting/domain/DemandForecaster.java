package com.uberlite.forecasting.domain;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class DemandForecaster {
    private final Map<String, Integer> demandByCell = new HashMap<>();

    @KafkaListener(topics = "trip-events", groupId = "forecasting-group")
    public void processTripEvent(String message) {
        // Placeholder: In production, this would parse the trip event,
        // extract the H3 cell and timestamp, and update the rolling average
        // for demand forecasting
        System.out.println("Forecasting Service received trip event: " + message);
    }

    public int forecastDemand(String h3Cell, String hourOfDay) {
        // Placeholder: return rolling average for this cell/hour
        return demandByCell.getOrDefault(h3Cell + ":" + hourOfDay, 10);
    }
}

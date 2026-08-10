package com.uberlite.discountspromotions.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class DiscountRuleFactory {
    private final ObjectMapper objectMapper;

    public DiscountRuleFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DiscountRule fromConditionJson(String conditionJson) {
        try {
            JsonNode node = objectMapper.readTree(conditionJson);
            String type = node.path("type").asText();
            if ("NEW_RIDER_TRIP_COUNT_LT".equals(type)) {
                return new NewRiderTripCountRule(node.path("value").asInt());
            }
            throw new IllegalArgumentException("Unsupported discount rule type: " + type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid discount rule condition JSON", e);
        }
    }
}

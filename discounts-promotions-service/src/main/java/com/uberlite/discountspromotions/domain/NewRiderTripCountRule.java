package com.uberlite.discountspromotions.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class NewRiderTripCountRule implements DiscountRule {
    private final int threshold;

    public NewRiderTripCountRule(int threshold) {
        this.threshold = threshold;
    }

    @Override
    public boolean evaluate(DiscountContext ctx) {
        return ctx.getRiderTripCount() < threshold;
    }

    public static NewRiderTripCountRule fromConditionJson(String conditionJson) {
        try {
            ObjectMapper om = new ObjectMapper();
            JsonNode n = om.readTree(conditionJson);
            int value = n.path("value").asInt(3);
            return new NewRiderTripCountRule(value);
        } catch (Exception e) {
            return new NewRiderTripCountRule(3);
        }
    }
}

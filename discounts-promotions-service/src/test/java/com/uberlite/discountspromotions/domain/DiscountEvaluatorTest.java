package com.uberlite.discountspromotions.domain;

import com.uberlite.discountspromotions.domain.DiscountEvaluator;
import com.uberlite.discountspromotions.domain.DiscountRuleFactory;
import com.uberlite.discountspromotions.repository.entity.PromoRuleEntity;
import com.uberlite.discountspromotions.repository.PromoRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscountEvaluatorTest {
    @Mock
    private PromoRuleRepository promoRuleRepository;

    private DiscountEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new DiscountEvaluator(promoRuleRepository, new DiscountRuleFactory(new com.fasterxml.jackson.databind.ObjectMapper()));
    }

    @Test
    void seededRuleMatchesNewRider() {
        when(promoRuleRepository.findAll()).thenReturn(List.of(seedRule()));

        assertThat(evaluator.evaluate("rider-1", 0)).isEqualTo(0.20);
    }

    @Test
    void seededRuleDoesNotMatchAtThreshold() {
        when(promoRuleRepository.findAll()).thenReturn(List.of(seedRule()));

        assertThat(evaluator.evaluate("rider-1", 3)).isEqualTo(0.0);
    }

    private static PromoRuleEntity seedRule() {
        PromoRuleEntity entity = new PromoRuleEntity();
        entity.setId("new-rider-first3");
        entity.setDescription("New rider first 3 trips");
        entity.setDiscountPct(new BigDecimal("0.20"));
        entity.setConditionJson("{\"type\":\"NEW_RIDER_TRIP_COUNT_LT\",\"value\":3}");
        return entity;
    }
}

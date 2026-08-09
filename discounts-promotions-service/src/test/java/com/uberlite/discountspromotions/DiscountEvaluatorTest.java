package com.uberlite.discountspromotions;

import com.uberlite.discountspromotions.domain.DiscountEvaluator;
import com.uberlite.discountspromotions.repository.PromoRuleEntity;
import com.uberlite.discountspromotions.repository.PromoRuleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class DiscountEvaluatorTest {

    @Autowired
    PromoRuleRepository repo;

    @Autowired
    DiscountEvaluator evaluator;

    @Test
    void seeded_rule_matches_new_rider() {
        // ensure seeded rule exists
        PromoRuleEntity r = repo.findById("new-rider-first3").orElseThrow();
        assertThat(r.getDiscountPct()).isEqualTo(new BigDecimal("0.20"));

        double pct = evaluator.evaluate("rider-1", 0);
        assertThat(pct).isEqualTo(0.20);
    }

    @Test
    void seeded_rule_not_match_on_trip_count_equal() {
        double pct = evaluator.evaluate("rider-1", 3);
        assertThat(pct).isEqualTo(0.0);
    }
}

package com.uberlite.discountspromotions;

import com.uberlite.discountspromotions.domain.DiscountEvaluator;
import com.uberlite.discountspromotions.repository.PromoRuleEntity;
import com.uberlite.discountspromotions.repository.PromoRuleRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

@SpringBootTest
@Testcontainers
public class DiscountEvaluatorTest {

    @Container
    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("discountsdb")
            .withUsername("uberlite")
            .withPassword("changeme");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

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

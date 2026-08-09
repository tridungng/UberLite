package com.uberlite.discountspromotions.domain;

import com.uberlite.discountspromotions.repository.PromoRuleEntity;
import com.uberlite.discountspromotions.repository.PromoRuleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class DiscountEvaluator {
    private final PromoRuleRepository promoRuleRepository;

    public DiscountEvaluator(PromoRuleRepository promoRuleRepository) {
        this.promoRuleRepository = promoRuleRepository;
    }

    public double evaluate(String riderId, int riderTripCount) {
        DiscountContext ctx = new DiscountContext(riderId, riderTripCount);
        List<PromoRuleEntity> rules = promoRuleRepository.findAll();
        for (PromoRuleEntity r : rules) {
            String cond = r.getConditionJson();
            if (cond != null && cond.contains("NEW_RIDER_TRIP_COUNT_LT")) {
                NewRiderTripCountRule rule = NewRiderTripCountRule.fromConditionJson(cond);
                if (rule.evaluate(ctx)) {
                    BigDecimal pct = r.getDiscountPct();
                    return pct == null ? 0.0 : pct.doubleValue();
                }
            }
        }
        return 0.0;
    }
}

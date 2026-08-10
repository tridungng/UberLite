package com.uberlite.discountspromotions.domain;

import com.uberlite.discountspromotions.repository.PromoRuleEntity;
import com.uberlite.discountspromotions.repository.PromoRuleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DiscountEvaluator {
    private final PromoRuleRepository promoRuleRepository;
    private final DiscountRuleFactory discountRuleFactory;

    public DiscountEvaluator(PromoRuleRepository promoRuleRepository, DiscountRuleFactory discountRuleFactory) {
        this.promoRuleRepository = promoRuleRepository;
        this.discountRuleFactory = discountRuleFactory;
    }

    public double evaluate(String riderId, int riderTripCount) {
        DiscountContext ctx = new DiscountContext(riderId, riderTripCount);
        List<PromoRuleEntity> rules = promoRuleRepository.findAll();
        for (PromoRuleEntity r : rules) {
            DiscountRule rule = discountRuleFactory.fromConditionJson(r.getConditionJson());
            if (rule.evaluate(ctx)) {
                return r.getDiscountPct() == null ? 0.0 : r.getDiscountPct().doubleValue();
            }
        }
        return 0.0;
    }
}

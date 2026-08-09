package com.uberlite.discountspromotions.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "promo_rules")
public class PromoRuleEntity {
    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "description")
    private String description;

    @Column(name = "discount_pct")
    private BigDecimal discountPct;

    @Column(name = "condition_json")
    private String conditionJson;

    public PromoRuleEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getDiscountPct() { return discountPct; }
    public void setDiscountPct(BigDecimal discountPct) { this.discountPct = discountPct; }
    public String getConditionJson() { return conditionJson; }
    public void setConditionJson(String conditionJson) { this.conditionJson = conditionJson; }
}

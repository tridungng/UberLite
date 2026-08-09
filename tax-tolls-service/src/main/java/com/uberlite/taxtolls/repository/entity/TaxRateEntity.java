package com.uberlite.taxtolls.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "tax_rates")
public class TaxRateEntity {
    @Id
    @Column(name = "region_id", nullable = false, length = 32)
    private String regionId;

    @Column(name = "rate", nullable = false)
    private BigDecimal rate;

    public TaxRateEntity() {}

    public TaxRateEntity(String regionId, BigDecimal rate) {
        this.regionId = regionId;
        this.rate = rate;
    }

    public String getRegionId() { return regionId; }
    public void setRegionId(String regionId) { this.regionId = regionId; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }
}

package com.uberlite.taxtolls.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "toll_segments")
public class TollSegmentEntity {
    @Id
    @Column(name = "route_id", nullable = false, length = 64)
    private String routeId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    public TollSegmentEntity() {}

    public TollSegmentEntity(String routeId, BigDecimal amount) {
        this.routeId = routeId;
        this.amount = amount;
    }

    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}

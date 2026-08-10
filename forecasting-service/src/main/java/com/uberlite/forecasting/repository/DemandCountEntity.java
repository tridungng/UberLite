package com.uberlite.forecasting.repository;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * One demand bucket: how many trips were requested in {@code h3Cell} during {@code hourOfDay} on
 * {@code dayBucket}.
 *
 * <p>Reads only. Writes go through {@link DemandCountRepository#incrementBucket} because a
 * load-modify-save round trip would lose increments under concurrent consumers.
 */
@Entity
@Table(name = "demand_counts")
public class DemandCountEntity {

    @EmbeddedId
    private DemandCountId id;

    @Column(name = "count", nullable = false)
    private long count;

    protected DemandCountEntity() {
    }

    public DemandCountId getId() {
        return id;
    }

    public long getCount() {
        return count;
    }
}


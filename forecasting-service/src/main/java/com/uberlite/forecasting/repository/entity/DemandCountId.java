package com.uberlite.forecasting.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/** Composite key of {@link DemandCountEntity}: one bucket per cell, hour-of-day and calendar day. */
@Embeddable
public class DemandCountId implements Serializable {

    @Column(name = "h3_cell", nullable = false, length = 32)
    private String h3Cell;

    @Column(name = "hour_of_day", nullable = false)
    private short hourOfDay;

    @Column(name = "day_bucket", nullable = false)
    private LocalDate dayBucket;

    protected DemandCountId() {
    }

    public DemandCountId(String h3Cell, int hourOfDay, LocalDate dayBucket) {
        this.h3Cell = h3Cell;
        this.hourOfDay = (short) hourOfDay;
        this.dayBucket = dayBucket;
    }

    public String getH3Cell() {
        return h3Cell;
    }

    public int getHourOfDay() {
        return hourOfDay;
    }

    public LocalDate getDayBucket() {
        return dayBucket;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DemandCountId that)) {
            return false;
        }
        return hourOfDay == that.hourOfDay
                && Objects.equals(h3Cell, that.h3Cell)
                && Objects.equals(dayBucket, that.dayBucket);
    }

    @Override
    public int hashCode() {
        return Objects.hash(h3Cell, hourOfDay, dayBucket);
    }
}


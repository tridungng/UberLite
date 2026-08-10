package com.uberlite.matchinganalytics.repository;

import com.uberlite.matchinganalytics.domain.MatchOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One matching outcome, as reconstructed from {@code trip-events}. */
@Entity
@Table(name = "match_log")
public class MatchLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false, length = 64)
    private String tripId;

    @Column(name = "driver_id", nullable = false, length = 64)
    private String driverId;

    /**
     * Stored as its name, not its ordinal: an ordinal makes the table unreadable and silently
     * rewrites history the first time a constant is inserted into the middle of the enum.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 16)
    private MatchOutcome outcome;

    /**
     * When the transition happened, taken from the event rather than from the consumer's own clock
     * — a replayed backlog would otherwise stamp every historical match with "now".
     */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected MatchLogEntity() {
    }

    public MatchLogEntity(String tripId, String driverId, MatchOutcome outcome, Instant occurredAt) {
        this.tripId = tripId;
        this.driverId = driverId;
        this.outcome = outcome;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public String getTripId() {
        return tripId;
    }

    public String getDriverId() {
        return driverId;
    }

    public MatchOutcome getOutcome() {
        return outcome;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}


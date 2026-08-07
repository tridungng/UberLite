package com.uberlite.tripservice.repository.entity;

import com.uberlite.common.events.TripState;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(schema = "trip", name = "trip_state_history")
public class TripStateHistoryEntity {
    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state")
    private TripState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false)
    private TripState toState;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private Map<String, Object> payload;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTripId() {
        return tripId;
    }

    public void setTripId(UUID tripId) {
        this.tripId = tripId;
    }

    public TripState getFromState() {
        return fromState;
    }

    public void setFromState(TripState fromState) {
        this.fromState = fromState;
    }

    public TripState getToState() {
        return toState;
    }

    public void setToState(TripState toState) {
        this.toState = toState;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}

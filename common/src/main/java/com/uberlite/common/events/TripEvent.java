package com.uberlite.common.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public class TripEvent {
    private final String tripId;
    private final TripState fromState;
    private final TripState toState;
    private final Instant timestamp;
    private final Map<String, Object> payload;

    @JsonCreator
    public TripEvent(@JsonProperty("tripId") String tripId,
                     @JsonProperty("fromState") TripState fromState,
                     @JsonProperty("toState") TripState toState,
                     @JsonProperty("timestamp") Instant timestamp,
                     @JsonProperty("payload") Map<String, Object> payload) {
        this.tripId = tripId;
        this.fromState = fromState;
        this.toState = toState;
        this.timestamp = timestamp;
        this.payload = payload;
    }

    public String getTripId() { return tripId; }
    public TripState getFromState() { return fromState; }
    public TripState getToState() { return toState; }
    public Instant getTimestamp() { return timestamp; }
    public Map<String, Object> getPayload() { return payload; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TripEvent)) return false;
        TripEvent that = (TripEvent) o;
        return Objects.equals(tripId, that.tripId) && fromState == that.fromState && toState == that.toState && Objects.equals(timestamp, that.timestamp) && Objects.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tripId, fromState, toState, timestamp, payload);
    }
}

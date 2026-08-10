package com.uberlite.matchinganalytics.domain;

import com.uberlite.common.events.TripState;

import java.util.Optional;

/**
 * What happened to a driver that Matching proposed for a trip.
 *
 * <p>A separate vocabulary from {@link TripState} on purpose: the trip state machine has twelve
 * states and this table cares about three of them, and re-using the enum would tie the analytics
 * schema to every future state the machine grows.
 */
public enum MatchOutcome {

    /** Matching picked this driver and Trip Service asked them (paper Sec. 3, Fig. 1). */
    PROPOSED,

    /** The driver took the trip. */
    ACCEPTED,

    /** The driver refused; Trip Service will retry with them excluded, up to k=3. */
    DECLINED;

    /**
     * @return the outcome a transition represents, or empty for the many trip transitions that say
     *     nothing about matching — the filter that keeps this table to matching events lives here,
     *     as one total function, rather than as an {@code if} chain in the Kafka listener
     */
    public static Optional<MatchOutcome> fromState(TripState toState) {
        if (toState == null) {
            return Optional.empty();
        }
        return switch (toState) {
            case DRIVER_PROPOSED -> Optional.of(PROPOSED);
            case DRIVER_ACCEPTED -> Optional.of(ACCEPTED);
            case DRIVER_DECLINED -> Optional.of(DECLINED);
            default -> Optional.empty();
        };
    }
}


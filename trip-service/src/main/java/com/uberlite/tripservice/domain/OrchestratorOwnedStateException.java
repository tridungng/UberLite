package com.uberlite.tripservice.domain;

import com.uberlite.common.events.TripState;

import java.util.Set;

/**
 * A client tried to move a trip into a state that only the orchestrator may produce.
 *
 * <p>{@code PRICED}, {@code DRIVER_PROPOSED} and {@code UNMATCHED} are <em>conclusions</em>: they
 * are only true because Price Estimation returned a quote or Matching returned (or failed to
 * return) a driver. Letting a client assert them directly would produce a {@code DRIVER_PROPOSED}
 * trip with no driver, or a priced trip with no price — states the rest of the system assumes
 * cannot exist.
 */
public class OrchestratorOwnedStateException extends RuntimeException {

    static final Set<TripState> ORCHESTRATOR_OWNED = Set.of(
            TripState.PRICED, TripState.DRIVER_PROPOSED, TripState.UNMATCHED);

    public OrchestratorOwnedStateException(TripState toState, String retryEndpoint) {
        super(toState + " is reached automatically once the responsible service answers and cannot "
                + "be set directly. Use " + retryEndpoint + " to retry that step.");
    }
}


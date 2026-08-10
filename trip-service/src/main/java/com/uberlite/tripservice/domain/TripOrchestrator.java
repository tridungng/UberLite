package com.uberlite.tripservice.domain;

import com.uberlite.common.dto.DriverCandidateDto;
import com.uberlite.common.dto.PriceQuoteDto;
import com.uberlite.common.events.TripState;
import com.uberlite.tripservice.api.dto.CreateTripRequest;
import com.uberlite.tripservice.api.dto.TransitionRequest;
import com.uberlite.tripservice.api.dto.TripResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Drives the end-to-end trip flow of ARCHITECTURE.md Sec. 4, turning downstream answers into state
 * transitions.
 *
 * <pre>
 *   POST /trips        -> REQUESTED -> [Price Estimation] -> PRICED       (+ surge pending++)
 *   -> ACCEPTED_BY_RIDER -> [Matching] -> DRIVER_PROPOSED | UNMATCHED
 *   -> DRIVER_DECLINED   -> [Matching, excluding decliners] -> DRIVER_PROPOSED | UNMATCHED
 *   -> COMPLETED | CANCELLED_BY_RIDER | UNMATCHED                          (+ surge pending--)
 * </pre>
 *
 * <h2>Why this is a separate class from {@link TripService}</h2>
 *
 * <p>{@code TripService} is transactional; this class is not. Every remote call below happens
 * <em>outside</em> a database transaction, so a slow dependency costs a request thread rather than a
 * pooled database connection. Each state change is then its own short transaction.
 *
 * <h2>Failure policy</h2>
 *
 * <ul>
 *   <li><b>Pricing failed</b> — the trip stays in {@code REQUESTED} and the caller gets a 502 that
 *       includes the trip id, so it can retry via {@code POST /trips/{id}/request-quote} instead of
 *       creating a duplicate trip.
 *   <li><b>Matching unreachable</b> — the trip stays where it is and no attempt is consumed
 *       (ARCHITECTURE.md Sec. 4). Re-sending the same transition retries the match.
 *   <li><b>Matching found nobody</b> — a real answer, so the trip moves to {@code UNMATCHED}.
 *   <li><b>Surge counter failed</b> — swallowed. A stale surge multiplier must never fail a trip.
 * </ul>
 */
@Service
public class TripOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TripOrchestrator.class);

    private final TripService tripService;
    private final TripStateMachine stateMachine;
    private final PricingGateway pricingGateway;
    private final MatchingGateway matchingGateway;
    private final SurgeSignalGateway surgeSignalGateway;

    public TripOrchestrator(TripService tripService,
                            TripStateMachine stateMachine,
                            PricingGateway pricingGateway,
                            MatchingGateway matchingGateway,
                            SurgeSignalGateway surgeSignalGateway) {
        this.tripService = tripService;
        this.stateMachine = stateMachine;
        this.pricingGateway = pricingGateway;
        this.matchingGateway = matchingGateway;
        this.surgeSignalGateway = surgeSignalGateway;
    }

    /**
     * Creates the trip in {@code REQUESTED} and immediately quotes it.
     *
     * <p>The trip row is committed before pricing is attempted, on purpose: if Price Estimation is
     * down the rider's intent is still recorded and retryable, rather than lost.
     */
    public TripResponse createAndQuote(CreateTripRequest request) {
        TripResponse trip = tripService.createTrip(request);
        return requestQuote(trip.id());
    }

    /**
     * Quotes a {@code REQUESTED} trip and moves it to {@code PRICED}. Also the retry path exposed as
     * {@code POST /trips/{id}/request-quote} when the quote at creation time failed.
     *
     * @throws DependencyFailedException if Price Estimation cannot produce a quote; the trip is left
     *                                   in {@code REQUESTED} and the call can simply be repeated
     */
    public TripResponse requestQuote(UUID tripId) {
        TripResponse trip = tripService.getTrip(tripId);
        // Fail fast with 409 rather than calling PES for a trip that cannot accept a quote anyway.
        stateMachine.validateTransition(trip.state(), TripState.PRICED, trip.attemptCount());

        PriceQuoteDto quote;
        try {
            quote = pricingGateway.quote(
                    trip.riderId(),
                    tripService.countCompletedTrips(trip.riderId()),
                    trip.pickup(),
                    trip.dropoff());
        } catch (DependencyFailedException e) {
            log.warn("Trip {} stays in REQUESTED: {}", tripId, e.getMessage());
            throw new TripDependencyFailedException(tripId, e);
        }

        TripResponse priced = tripService.applyQuote(tripId, quote);
        // The rider is now waiting in this cell, which is exactly the demand signal SPS prices on.
        registerSurgeDemand(tripId, priced.pickupH3());
        return priced;
    }

    /**
     * Applies a client-driven transition, then performs whatever cross-service work that transition
     * implies.
     *
     * @throws OrchestratorOwnedStateException if the caller tries to assert a state that only a
     *                                         downstream answer can justify
     */
    public TripResponse transition(UUID tripId, TransitionRequest request) {
        rejectOrchestratorOwnedState(tripId, request.toState());

        TripResponse trip = tripService.transitionTrip(tripId, request);

        return switch (trip.state()) {
            // The rider committed to the quote: go find a driver.
            case ACCEPTED_BY_RIDER -> matchDriver(tripId, "no drivers available near the pickup");
            // The driver said no. The retry budget was just decremented inside the transition.
            case DRIVER_DECLINED -> afterDecline(tripId, trip);
            // The trip has left the matching pipeline; stop counting it as waiting demand.
            case COMPLETED, CANCELLED_BY_RIDER -> {
                releaseSurgeDemand(tripId, trip.pickupH3());
                yield trip;
            }
            default -> trip;
        };
    }

    /**
     * Retry path for a trip whose match attempt failed because Matching was unreachable, exposed as
     * {@code POST /trips/{id}/request-match}. Safe to repeat: a failed attempt consumed no budget,
     * so this resumes exactly where the trip was left.
     */
    public TripResponse requestMatch(UUID tripId) {
        TripResponse trip = tripService.getTrip(tripId);
        // 409 rather than a pointless call to Matching for a trip that cannot accept a driver.
        stateMachine.validateTransition(trip.state(), TripState.DRIVER_PROPOSED, trip.attemptCount());
        return matchDriver(tripId, "no drivers available near the pickup");
    }

    /**
     * {@code PRICED}, {@code DRIVER_PROPOSED} and {@code UNMATCHED} are conclusions drawn from a
     * downstream answer, never client assertions — otherwise a client could produce a
     * {@code DRIVER_PROPOSED} trip with no driver on it.
     */
    private void rejectOrchestratorOwnedState(UUID tripId, TripState toState) {
        if (!OrchestratorOwnedStateException.ORCHESTRATOR_OWNED.contains(toState)) {
            return;
        }
        String retryEndpoint = toState == TripState.PRICED
                ? "POST /trips/" + tripId + "/request-quote"
                : "POST /trips/" + tripId + "/request-match";
        throw new OrchestratorOwnedStateException(toState, retryEndpoint);
    }

    private TripResponse afterDecline(UUID tripId, TripResponse trip) {
        if (stateMachine.hasExhaustedAttempts(trip.attemptCount())) {
            log.info("Trip {} exhausted the k={} matching budget after declines by {}",
                    tripId, TripStateMachine.MAX_MATCH_ATTEMPTS, trip.declinedDriverIds());
            return markUnmatched(tripId, trip.pickupH3(),
                    "retry budget of " + TripStateMachine.MAX_MATCH_ATTEMPTS + " attempts exhausted");
        }
        return matchDriver(tripId, "no drivers available other than those who already declined");
    }

    /**
     * Asks Matching for a driver and applies the answer.
     *
     * @param noDriverReason recorded on the {@code UNMATCHED} event when the marketplace is empty
     */
    private TripResponse matchDriver(UUID tripId, String noDriverReason) {
        TripResponse trip = tripService.getTrip(tripId);

        // A DependencyFailedException here propagates as 502 on purpose: the trip stays put and no
        // attempt is consumed, so an outage never parks a trip in UNMATCHED.
        Optional<DriverCandidateDto> candidate;
        try {
            candidate = matchingGateway.proposeDriver(tripId, trip.pickup(), trip.declinedDriverIds());
        } catch (DependencyFailedException e) {
            log.warn("Trip {} stays in {}: {}", tripId, trip.state(), e.getMessage());
            throw new TripDependencyFailedException(tripId, e);
        }

        if (candidate.isEmpty()) {
            return markUnmatched(tripId, trip.pickupH3(), noDriverReason);
        }
        return tripService.applyProposedDriver(tripId, candidate.get());
    }

    private TripResponse markUnmatched(UUID tripId, String pickupH3, String reason) {
        TripResponse unmatched = tripService.markUnmatched(tripId, reason);
        // MVP has no notification service (see the issue); the log is the notification.
        log.warn("NOTIFY rider {}: trip {} could not be matched — {}",
                unmatched.riderId(), tripId, reason);
        releaseSurgeDemand(tripId, pickupH3);
        return unmatched;
    }

    /**
     * The claim/release flag is flipped in its own transaction <em>before</em> the remote call, so
     * two concurrent callers cannot both send the increment. If the remote call then fails, SPS's
     * counter drifts by one until it expires — a deliberately cheaper failure than a rider who
     * cannot book because the pricing service is unwell.
     */
    private void registerSurgeDemand(UUID tripId, String pickupH3) {
        if (tripService.claimSurgePending(tripId)) {
            surgeSignalGateway.incrementPending(pickupH3);
        }
    }

    private void releaseSurgeDemand(UUID tripId, String pickupH3) {
        if (tripService.releaseSurgePending(tripId)) {
            surgeSignalGateway.decrementPending(pickupH3);
        }
    }
}



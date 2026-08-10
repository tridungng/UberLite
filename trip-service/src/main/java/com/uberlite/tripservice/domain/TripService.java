package com.uberlite.tripservice.domain;

import com.uberlite.common.dto.DriverCandidateDto;
import com.uberlite.common.dto.LocationDto;
import com.uberlite.common.dto.PriceQuoteDto;
import com.uberlite.common.dto.RiderTripCountDto;
import com.uberlite.common.events.Topics;
import com.uberlite.common.events.TripEvent;
import com.uberlite.common.events.TripEventPayloadKeys;
import com.uberlite.common.events.TripState;
import com.uberlite.common.geo.H3Util;
import com.uberlite.tripservice.api.dto.CreateTripRequest;
import com.uberlite.tripservice.api.dto.TransitionRequest;
import com.uberlite.tripservice.api.dto.TripHistoryDto;
import com.uberlite.tripservice.api.dto.TripResponse;
import com.uberlite.tripservice.repository.TripRepository;
import com.uberlite.tripservice.repository.TripStateHistoryRepository;
import com.uberlite.tripservice.repository.entity.TripEntity;
import com.uberlite.tripservice.repository.entity.TripStateHistoryEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The transactional core of the trip state machine: it owns the Postgres row, the history table and
 * the {@code trip-events} stream, and it is the only place a trip's state ever changes.
 *
 * <p><b>No remote calls happen here, deliberately.</b> Every public method runs inside a database
 * transaction, and holding a connection open across a synchronous HTTP call to another service is
 * how one slow dependency becomes a connection-pool outage. Cross-service choreography lives in
 * {@link TripOrchestrator}, which composes these short transactions.
 *
 * <p>The Kafka publish <em>is</em> inside the transaction and blocks on the broker ack: a state
 * change nobody is told about is worse than one that did not happen, since Matching Analytics and
 * Discounts Analytics reconstruct trips from this stream alone.
 */
@Service
public class TripService {

    private final TripRepository tripRepository;
    private final TripStateHistoryRepository historyRepository;
    private final TripStateMachine stateMachine;
    private final KafkaTemplate<String, TripEvent> kafkaTemplate;

    public TripService(TripRepository tripRepository,
                       TripStateHistoryRepository historyRepository,
                       TripStateMachine stateMachine,
                       KafkaTemplate<String, TripEvent> kafkaTemplate) {
        this.tripRepository = tripRepository;
        this.historyRepository = historyRepository;
        this.stateMachine = stateMachine;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public TripResponse createTrip(CreateTripRequest request) {
        TripEntity trip = new TripEntity();
        trip.setRiderId(request.riderId());
        trip.setState(TripState.REQUESTED);
        trip.setAttemptCount(0);
        applyLocation(trip, request.pickup(), request.dropoff());

        trip = tripRepository.save(trip);

        Instant occurredAt = Instant.now();
        Map<String, Object> payload = createPayload(request);
        historyRepository.save(historyRow(trip.getId(), null, TripState.REQUESTED, payload, occurredAt));
        // Creation is the null -> REQUESTED transition and is published like any other, so a
        // consumer sees a complete history rather than one that starts at PRICED.
        publishTripEvent(trip.getId(), null, TripState.REQUESTED, occurredAt, payload);

        return toResponse(trip);
    }

    @Transactional(readOnly = true)
    public TripResponse getTrip(UUID tripId) {
        return toResponse(loadTrip(tripId));
    }

    /**
     * Completed trips for a rider, used as the {@code riderTripCount} pricing input. Trip Service is
     * the only holder of this figure and Discounts and Promotions keys its "first N rides" rules on
     * it, so it is counted rather than assumed to be zero.
     */
    @Transactional(readOnly = true)
    public int countCompletedTrips(String riderId) {
        return (int) tripRepository.countByRiderIdAndStateIn(
                riderId, List.of(TripState.COMPLETED, TripState.PAID));
    }

    /**
     * The same figure for every rider that has at least one completed trip. Discounts Analytics'
     * nightly batch (ARCHITECTURE.md Sec. 2) needs the whole population, and one HTTP call per
     * rider would make the batch's cost linear in a number it does not know up front.
     *
     * <p>Riders with zero completed trips are absent by construction — a {@code GROUP BY} over
     * completed trips cannot invent a row for someone who has none. Callers that treat "below the
     * promo threshold" as including brand-new riders must handle that themselves; Trip Service does
     * not know who has merely registered.
     */
    @Transactional(readOnly = true)
    public List<RiderTripCountDto> countCompletedTripsPerRider() {
        return tripRepository.countTripsPerRider(List.of(TripState.COMPLETED, TripState.PAID))
                .stream()
                .map(row -> new RiderTripCountDto(row.getRiderId(), row.getTripCount()))
                .toList();
    }

    /**
     * A client-driven transition. Auto-transitions justified by a downstream answer use the
     * dedicated methods below, so the evidence for the move is written in the same transaction.
     */
    @Transactional
    public TripResponse transitionTrip(UUID tripId, TransitionRequest request) {
        TripEntity trip = loadTrip(tripId);
        Map<String, Object> payload = new LinkedHashMap<>(safePayload(request.payload()));

        return applyTransition(trip, request.toState(), payload, entity -> {
            if (request.toState() == TripState.DRIVER_DECLINED) {
                recordDecline(entity, payload);
            }
        });
    }

    /** {@code REQUESTED -> PRICED}, carrying the quote we have committed to. */
    @Transactional
    public TripResponse applyQuote(UUID tripId, PriceQuoteDto quote) {
        TripEntity trip = loadTrip(tripId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(TripEventPayloadKeys.QUOTED_PRICE, quote.getAmount());
        payload.put(TripEventPayloadKeys.CURRENCY, quote.getCurrency());
        payload.put(TripEventPayloadKeys.PRICE_BREAKDOWN, quote.getBreakdown());

        return applyTransition(trip, TripState.PRICED, payload, entity -> {
            entity.setQuotedPrice(BigDecimal.valueOf(quote.getAmount()).setScale(2, RoundingMode.HALF_UP));
            entity.setQuoteCurrency(quote.getCurrency());
            entity.setQuoteBreakdown(quote.getBreakdown());
        });
    }

    /** {@code ACCEPTED_BY_RIDER | DRIVER_DECLINED -> DRIVER_PROPOSED} with the matched driver. */
    @Transactional
    public TripResponse applyProposedDriver(UUID tripId, DriverCandidateDto driver) {
        TripEntity trip = loadTrip(tripId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(TripEventPayloadKeys.DRIVER_ID, driver.getDriverId());
        payload.put(TripEventPayloadKeys.ETA_SECONDS, driver.getEtaSeconds());
        payload.put(TripEventPayloadKeys.ATTEMPT, trip.getAttemptCount() + 1);

        return applyTransition(trip, TripState.DRIVER_PROPOSED, payload,
                entity -> entity.setDriverId(driver.getDriverId()));
    }

    /** Terminal {@code -> UNMATCHED}: nobody will drive this trip. */
    @Transactional
    public TripResponse markUnmatched(UUID tripId, String reason) {
        TripEntity trip = loadTrip(tripId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(TripEventPayloadKeys.REASON, reason);
        payload.put(TripEventPayloadKeys.ATTEMPT, trip.getAttemptCount());
        payload.put(TripEventPayloadKeys.DECLINED_DRIVER_IDS, trip.getDeclinedDriverIds());

        return applyTransition(trip, TripState.UNMATCHED, payload, entity -> entity.setDriverId(null));
    }

    /**
     * Claims this trip's slot in the Surge pending-request gauge.
     *
     * @return true if the caller now owns the increment; false if it was already counted, which is
     *     what makes a retried quote safe
     */
    @Transactional
    public boolean claimSurgePending(UUID tripId) {
        TripEntity trip = loadTrip(tripId);
        if (trip.isSurgePendingRegistered()) {
            return false;
        }
        trip.setSurgePendingRegistered(true);
        tripRepository.save(trip);
        return true;
    }

    /**
     * Releases this trip's slot in the Surge pending-request gauge.
     *
     * @return true if the caller now owns the decrement; false if it was never counted or has
     *     already been released, which is what makes a re-sent terminal transition safe
     */
    @Transactional
    public boolean releaseSurgePending(UUID tripId) {
        TripEntity trip = loadTrip(tripId);
        if (!trip.isSurgePendingRegistered()) {
            return false;
        }
        trip.setSurgePendingRegistered(false);
        tripRepository.save(trip);
        return true;
    }

    /**
     * The single write path for a state change: validate, mutate, persist, record history, publish.
     * Every caller goes through here, so no transition can skip the event.
     */
    private TripResponse applyTransition(TripEntity trip,
                                         TripState toState,
                                         Map<String, Object> payload,
                                         Consumer<TripEntity> mutator) {
        TripState fromState = trip.getState();
        stateMachine.validateTransition(fromState, toState, trip.getAttemptCount());

        mutator.accept(trip);
        trip.setState(toState);
        tripRepository.save(trip);

        Instant occurredAt = Instant.now();
        historyRepository.save(historyRow(trip.getId(), fromState, toState, payload, occurredAt));
        publishTripEvent(trip.getId(), fromState, toState, occurredAt, payload);

        return toResponse(trip);
    }

    /**
     * A decline costs one attempt from the k=3 budget and permanently excludes that driver, so
     * Matching's stateless answer can be filtered on the retry.
     */
    private void recordDecline(TripEntity trip, Map<String, Object> payload) {
        String declinedDriverId = trip.getDriverId();
        trip.addDeclinedDriver(declinedDriverId);
        trip.setDriverId(null);
        trip.setAttemptCount(trip.getAttemptCount() + 1);

        payload.putIfAbsent(TripEventPayloadKeys.DRIVER_ID, declinedDriverId);
        payload.put(TripEventPayloadKeys.ATTEMPT, trip.getAttemptCount());
        payload.put(TripEventPayloadKeys.DECLINED_DRIVER_IDS, trip.getDeclinedDriverIds());
    }

    private TripEntity loadTrip(UUID tripId) {
        return tripRepository.findById(tripId).orElseThrow(() -> new TripNotFoundException(tripId));
    }

    private void publishTripEvent(UUID tripId,
                                  TripState fromState,
                                  TripState toState,
                                  Instant timestamp,
                                  Map<String, Object> payload) {
        TripEvent event = new TripEvent(tripId.toString(), fromState, toState, timestamp, payload);
        try {
            // Keyed by tripId so one trip's transitions stay ordered within a partition.
            kafkaTemplate.send(Topics.TRIP_EVENTS, event.getTripId(), event).join();
        } catch (RuntimeException e) {
            // Rolls the transaction back: better to fail the caller's request than to leave the
            // database and the event stream disagreeing about what happened.
            throw new IllegalStateException("Failed to publish trip event for trip " + tripId, e);
        }
    }

    private TripStateHistoryEntity historyRow(UUID tripId,
                                              TripState fromState,
                                              TripState toState,
                                              Map<String, Object> payload,
                                              Instant occurredAt) {
        TripStateHistoryEntity history = new TripStateHistoryEntity();
        history.setTripId(tripId);
        history.setFromState(fromState);
        history.setToState(toState);
        history.setOccurredAt(occurredAt);
        history.setPayload(payload);
        return history;
    }

    private Map<String, Object> createPayload(CreateTripRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(TripEventPayloadKeys.RIDER_ID, request.riderId());
        payload.put(TripEventPayloadKeys.PICKUP, locationPayload(request.pickup()));
        payload.put(TripEventPayloadKeys.DROPOFF, locationPayload(request.dropoff()));
        return payload;
    }

    private Map<String, Object> locationPayload(LocationDto location) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("lat", location.getLat());
        payload.put("lon", location.getLon());
        return payload;
    }

    private Map<String, Object> safePayload(Map<String, Object> payload) {
        return payload == null ? Map.of() : payload;
    }

    private void applyLocation(TripEntity trip, LocationDto pickup, LocationDto dropoff) {
        trip.setPickupLat(pickup.getLat());
        trip.setPickupLon(pickup.getLon());
        trip.setPickupH3(H3Util.latLngToCell(pickup.getLat(), pickup.getLon()));
        trip.setDropoffLat(dropoff.getLat());
        trip.setDropoffLon(dropoff.getLon());
        trip.setDropoffH3(H3Util.latLngToCell(dropoff.getLat(), dropoff.getLon()));
    }

    private TripResponse toResponse(TripEntity trip) {
        List<TripHistoryDto> history = historyRepository.findByTripIdOrderByOccurredAtAscIdAsc(trip.getId())
                .stream()
                .map(this::toHistoryDto)
                .toList();

        return new TripResponse(
                trip.getId(),
                trip.getRiderId(),
                new LocationDto(trip.getPickupLat(), trip.getPickupLon()),
                new LocationDto(trip.getDropoffLat(), trip.getDropoffLon()),
                trip.getPickupH3(),
                trip.getDropoffH3(),
                trip.getState(),
                trip.getQuotedPrice(),
                trip.getQuoteCurrency(),
                trip.getQuoteBreakdown(),
                trip.getDriverId(),
                trip.getDeclinedDriverIds(),
                trip.getAttemptCount(),
                trip.getCreatedAt(),
                trip.getUpdatedAt(),
                history
        );
    }

    private TripHistoryDto toHistoryDto(TripStateHistoryEntity history) {
        return new TripHistoryDto(
                history.getId(),
                history.getFromState(),
                history.getToState(),
                history.getOccurredAt(),
                history.getPayload()
        );
    }
}


package com.uberlite.tripservice.domain;

import com.uberlite.common.dto.LocationDto;
import com.uberlite.common.events.Topics;
import com.uberlite.common.events.TripEvent;
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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
        historyRepository.save(createHistoryRow(trip.getId(), null, TripState.REQUESTED, createCreatePayload(request)));

        return toResponse(trip);
    }

    @Transactional(readOnly = true)
    public TripResponse getTrip(UUID tripId) {
        TripEntity trip = tripRepository.findById(tripId).orElseThrow(() -> new TripNotFoundException(tripId));
        return toResponse(trip);
    }

    @Transactional
    public TripResponse transitionTrip(UUID tripId, TransitionRequest request) {
        TripEntity trip = tripRepository.findById(tripId).orElseThrow(() -> new TripNotFoundException(tripId));
        TripState fromState = trip.getState();
        stateMachine.validateTransition(fromState, request.toState(), trip.getAttemptCount());

        if (fromState == TripState.DRIVER_PROPOSED && request.toState() == TripState.DRIVER_DECLINED) {
            trip.setAttemptCount(trip.getAttemptCount() + 1);
        }

        trip.setState(request.toState());
        tripRepository.save(trip);

        Map<String, Object> payload = safePayload(request.payload());
        Instant occurredAt = Instant.now();
        historyRepository.save(createHistoryRow(trip.getId(), fromState, request.toState(), payload, occurredAt));
        publishTripEvent(trip.getId(), fromState, request.toState(), occurredAt, payload);

        return toResponse(trip);
    }

    private void publishTripEvent(UUID tripId, TripState fromState, TripState toState, Instant timestamp, Map<String, Object> payload) {
        TripEvent event = new TripEvent(tripId.toString(), fromState, toState, timestamp, payload);
        try {
            kafkaTemplate.send(Topics.TRIP_EVENTS, event.getTripId(), event).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish trip event", e);
        }
    }

    private TripStateHistoryEntity createHistoryRow(UUID tripId, TripState fromState, TripState toState, Map<String, Object> payload) {
        return createHistoryRow(tripId, fromState, toState, payload, Instant.now());
    }

    private TripStateHistoryEntity createHistoryRow(UUID tripId, TripState fromState, TripState toState, Map<String, Object> payload, Instant occurredAt) {
        TripStateHistoryEntity history = new TripStateHistoryEntity();
        history.setTripId(tripId);
        history.setFromState(fromState);
        history.setToState(toState);
        history.setOccurredAt(occurredAt);
        history.setPayload(payload);
        return history;
    }

    private Map<String, Object> createCreatePayload(CreateTripRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("riderId", request.riderId());
        payload.put("pickup", locationPayload(request.pickup()));
        payload.put("dropoff", locationPayload(request.dropoff()));
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

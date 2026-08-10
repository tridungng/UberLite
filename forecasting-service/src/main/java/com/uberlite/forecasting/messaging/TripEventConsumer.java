package com.uberlite.forecasting.messaging;

import com.uberlite.common.events.Topics;
import com.uberlite.common.events.TripEvent;
import com.uberlite.common.events.kafka.TripEventConsumerConfiguration;
import com.uberlite.forecasting.domain.DemandRecorder;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Subscribes to the trigger framework (ARCHITECTURE.md Sec. 3) and feeds requested trips into the
 * demand buckets.
 *
 * <p>Thin on purpose: it owns the Kafka concern only, so {@link DemandRecorder} stays testable
 * without a broker and the filtering rule stays testable without a listener container.
 */
@Component
public class TripEventConsumer {

    private final DemandRecorder recorder;

    public TripEventConsumer(DemandRecorder recorder) {
        this.recorder = recorder;
    }

    /**
     * Demand is measured at {@code REQUESTED}, not at {@code COMPLETED}: the paper's forecast is of
     * demand, and a rider who requested a ride and got none is exactly the demand a surge signal
     * needs to see. Filtering happens in {@link DemandRecorder} so the rule is unit-testable.
     */
    @KafkaListener(topics = Topics.TRIP_EVENTS, groupId = "${spring.kafka.consumer.group-id}")
    public void onTripEvent(TripEvent event) {
        recorder.record(event);
    }
}



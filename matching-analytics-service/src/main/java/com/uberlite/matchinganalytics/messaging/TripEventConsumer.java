package com.uberlite.matchinganalytics.messaging;

import com.uberlite.common.events.Topics;
import com.uberlite.common.events.TripEvent;
import com.uberlite.common.events.kafka.TripEventConsumerConfiguration;
import com.uberlite.matchinganalytics.domain.MatchingEventLogger;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Subscribes to the trigger framework (ARCHITECTURE.md Sec. 3). Trip Service does not know this
 * consumer exists, which is the point of publishing transitions rather than calling analytics
 * directly.
 */
@Component
public class TripEventConsumer {

    private final MatchingEventLogger logger;

    public TripEventConsumer(MatchingEventLogger logger) {
        this.logger = logger;
    }

    @KafkaListener(topics = Topics.TRIP_EVENTS, groupId = "${spring.kafka.consumer.group-id}")
    public void onTripEvent(TripEvent event) {
        logger.record(event);
    }
}



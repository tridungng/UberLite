package com.uberlite.common.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TripEventTest {
    @Test
    public void tripEventJsonRoundTrip() throws Exception {
        TripEvent ev = new TripEvent("trip-1", TripState.REQUESTED, TripState.PRICED, Instant.now(), Map.of("k","v"));
        ObjectMapper m = new ObjectMapper();
        String json = m.writeValueAsString(ev);
        TripEvent ev2 = m.readValue(json, TripEvent.class);
        assertEquals(ev.getTripId(), ev2.getTripId());
        assertEquals(ev.getFromState(), ev2.getFromState());
        assertEquals(ev.getToState(), ev2.getToState());
        assertEquals(ev.getPayload(), ev2.getPayload());
    }
}

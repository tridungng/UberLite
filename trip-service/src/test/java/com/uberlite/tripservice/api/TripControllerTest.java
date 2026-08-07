package com.uberlite.tripservice.api;

import com.uberlite.common.dto.LocationDto;
import com.uberlite.common.events.TripState;
import com.uberlite.tripservice.api.dto.CreateTripRequest;
import com.uberlite.tripservice.api.dto.TransitionRequest;
import com.uberlite.tripservice.api.dto.TripHistoryDto;
import com.uberlite.tripservice.api.dto.TripResponse;
import com.uberlite.tripservice.domain.TripService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.boot.test.context.SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:tripdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@Deprecated
class TripControllerTest {
    private MockMvc mockMvc;

    private final StubTripService tripService = new StubTripService();

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        this.mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                        new TripController(tripService), new ApiExceptionHandler())
                .build();
    }

    @Test
    void createTripReturnsCreatedTrip() throws Exception {
        UUID id = UUID.randomUUID();
        tripService.createResponder = request -> new TripResponse(
                id,
                request.riderId(),
                request.pickup(),
                request.dropoff(),
                "pickup-h3",
                "dropoff-h3",
                TripState.REQUESTED,
                0,
                Instant.parse("2026-08-05T00:00:00Z"),
                Instant.parse("2026-08-05T00:00:00Z"),
                List.of());

        mockMvc.perform(post("/trips").contentType(MediaType.APPLICATION_JSON).content("""
            {"riderId":"rider-1","pickup":{"lat":1.0,"lon":2.0},"dropoff":{"lat":3.0,"lon":4.0}}
            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.state").value("REQUESTED"));
    }

    @Test
    void illegalTransitionReturnsConflict() throws Exception {
        tripService.transitionResponder = (id, request) -> {
            throw new com.uberlite.tripservice.domain.IllegalTransitionException(
                    TripState.REQUESTED, request.toState(), java.util.EnumSet.of(TripState.PRICED));
        };

        mockMvc.perform(post("/trips/11111111-1111-1111-1111-111111111111/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"toState":"DRIVER_PROPOSED","payload":{"reason":"bad"}}
                            """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Illegal transition from REQUESTED to DRIVER_PROPOSED. Allowed next states: [PRICED]"));
    }

    @Test
    void getTripReturnsTrip() throws Exception {
        UUID id = UUID.randomUUID();
        tripService.getResponder = tripId -> new TripResponse(
                id,
                "rider-1",
                new LocationDto(1.0, 2.0),
                new LocationDto(3.0, 4.0),
                "pickup-h3",
                "dropoff-h3",
                TripState.REQUESTED,
                0,
                Instant.parse("2026-08-05T00:00:00Z"),
                Instant.parse("2026-08-05T00:00:00Z"),
                List.of(new TripHistoryDto(
                        UUID.randomUUID(),
                        null,
                        TripState.REQUESTED,
                        Instant.parse("2026-08-05T00:00:00Z"),
                        Map.of())));

        mockMvc.perform(get("/trips/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history[0].toState").value("REQUESTED"));
    }

    @TestConfiguration
    static class StubConfig {
        @Bean(name = "stubTripService")
        @org.springframework.context.annotation.Primary
        StubTripService stubTripService() {
            return new StubTripService();
        }
    }

    static class StubTripService extends TripService {
        Function<CreateTripRequest, TripResponse> createResponder = request -> {
            throw new IllegalStateException("create responder not configured");
        };
        Function<UUID, TripResponse> getResponder = id -> {
            throw new IllegalStateException("get responder not configured");
        };
        BiFunction<UUID, TransitionRequest, TripResponse> transitionResponder = (id, request) -> {
            throw new IllegalStateException("transition responder not configured");
        };

        StubTripService() {
            super(null, null, null, null);
        }

        @Override
        public TripResponse createTrip(CreateTripRequest request) {
            return createResponder.apply(request);
        }

        @Override
        public TripResponse getTrip(UUID tripId) {
            return getResponder.apply(tripId);
        }

        @Override
        public TripResponse transitionTrip(UUID tripId, TransitionRequest request) {
            return transitionResponder.apply(tripId, request);
        }
    }
}

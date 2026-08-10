package com.uberlite.tripservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uberlite.common.events.Topics;
import com.uberlite.common.events.TripEvent;
import com.uberlite.common.events.TripState;
import com.uberlite.common.geo.H3Util;
import com.uberlite.common.testing.StubServer;
import com.uberlite.common.testing.StubServer.Stub;
import com.uberlite.tripservice.repository.TripRepository;
import com.uberlite.tripservice.repository.entity.TripEntity;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end orchestration: real Postgres-shaped schema, real embedded Kafka, and the three
 * downstream services stubbed over real HTTP.
 *
 * <p>Eureka is switched off and every {@code @FeignClient} service id is pointed at the stub server
 * through the SimpleDiscoveryClient, so the real Feign clients, real JSON codecs and real URLs are
 * exercised. That is what catches a client annotation that disagrees with a downstream route — for
 * example {@code POST /surge/{cell}/pending-request} vs {@code DELETE} — which a Mockito test
 * cannot.
 *
 * <p>{@code StubServer} stands in for WireMock, which cannot run on a Spring Boot 4 (Jakarta-only)
 * classpath; the reasoning is documented on the class itself.
 */
@SpringBootTest(
        properties = {
            "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
            "spring.datasource.url=jdbc:h2:mem:triporchestration;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
            "spring.jpa.properties.hibernate.default_schema=trip",
            "spring.flyway.enabled=false"
        })
@EmbeddedKafka(partitions = 1, topics = Topics.TRIP_EVENTS)
class TripOrchestrationIntegrationTest {

    private static final StubServer STUBS = new StubServer();

    private static final double PICKUP_LAT = 37.7749;
    private static final double PICKUP_LON = -122.4194;
    /** The surge counter is keyed on the pickup cell, so the stub route must match it exactly. */
    private static final String PICKUP_CELL = H3Util.latLngToCell(PICKUP_LAT, PICKUP_LON);
    private static final String PENDING_REQUEST_PATH = "/surge/" + PICKUP_CELL + "/pending-request";

    private static final String CREATE_TRIP_BODY = """
            {
              "riderId": "rider-1",
              "pickup":  {"lat": 37.7749, "lon": -122.4194},
              "dropoff": {"lat": 37.8044, "lon": -122.2712}
            }
            """;

    private static final String QUOTE_JSON = """
            {"amount":25.50,"currency":"USD","breakdown":{"distanceKm":11.0,"surgeMultiplier":1.5}}
            """;

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @Autowired private WebApplicationContext context;
    @Autowired private EmbeddedKafkaBroker embeddedKafka;
    @Autowired private TripRepository tripRepository;

    private MockMvc mockMvc;
    private Consumer<String, String> consumer;

    @DynamicPropertySource
    static void downstreamUris(DynamicPropertyRegistry registry) {
        String uri = STUBS.baseUrl();
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.discovery.client.simple.instances.price-estimation-service[0].uri", () -> uri);
        registry.add("spring.cloud.discovery.client.simple.instances.matching-service[0].uri", () -> uri);
        registry.add("spring.cloud.discovery.client.simple.instances.surge-pricing-service[0].uri", () -> uri);
    }

    @AfterAll
    static void stopStubs() {
        STUBS.close();
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        stubHappyPath();
        consumer = createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, Topics.TRIP_EVENTS);
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    private void stubHappyPath() {
        STUBS.reset();
        STUBS.stub("/price-estimates", Stub.okJson(QUOTE_JSON))
                .stub("/matches", Stub.okJson(driverJson("driver-1")))
                // POST (increment) and DELETE (decrement) share a path; they are told apart by the
                // recorded method below.
                .stub(PENDING_REQUEST_PATH, Stub.okJson("{}"));
    }

    // ---------------------------------------------------------------------------------------
    // Acceptance criterion 1: full happy-path lifecycle
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("POST /trips -> auto-PRICED -> accept -> auto-DRIVER_PROPOSED -> pickup -> complete -> pay")
    void fullTripLifecycle() throws Exception {
        UUID tripId = createTripExpectingPriced();

        // Price Estimation was consulted with the rider context it needs for promotions.
        assertThat(STUBS.requestTo("/price-estimates").body())
                .contains("\"riderId\":\"rider-1\"")
                .contains("\"riderTripCount\":0");
        // Entering the matching pipeline registers demand for surge.
        assertThat(countRequests("POST", PENDING_REQUEST_PATH)).isEqualTo(1);

        // Rider accepts -> Matching is called -> auto-transition to DRIVER_PROPOSED.
        transition(tripId, TripState.ACCEPTED_BY_RIDER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DRIVER_PROPOSED"))
                .andExpect(jsonPath("$.driverId").value("driver-1"))
                .andExpect(jsonPath("$.attemptCount").value(0));
        assertThat(STUBS.requestTo("/matches").body()).contains("\"tripId\":\"" + tripId + "\"");

        transition(tripId, TripState.DRIVER_ACCEPTED).andExpect(jsonPath("$.state").value("DRIVER_ACCEPTED"));
        transition(tripId, TripState.EN_ROUTE_TO_PICKUP).andExpect(jsonPath("$.state").value("EN_ROUTE_TO_PICKUP"));
        transition(tripId, TripState.RIDER_PICKED_UP).andExpect(jsonPath("$.state").value("RIDER_PICKED_UP"));

        // Completing the trip takes it out of the matching pipeline: surge demand is released.
        transition(tripId, TripState.COMPLETED).andExpect(jsonPath("$.state").value("COMPLETED"));
        assertThat(countRequests("DELETE", PENDING_REQUEST_PATH)).isEqualTo(1);

        transition(tripId, TripState.PAID).andExpect(jsonPath("$.state").value("PAID"));

        TripEntity persisted = tripRepository.findById(tripId).orElseThrow();
        assertThat(persisted.getState()).isEqualTo(TripState.PAID);
        assertThat(persisted.getDriverId()).isEqualTo("driver-1");
        assertThat(persisted.getQuotedPrice()).isEqualByComparingTo("25.50");
        assertThat(persisted.isSurgePendingRegistered()).isFalse();

        assertThat(statesOf(drainEvents(tripId, 9))).containsExactly(
                TripState.REQUESTED,
                TripState.PRICED,
                TripState.ACCEPTED_BY_RIDER,
                TripState.DRIVER_PROPOSED,
                TripState.DRIVER_ACCEPTED,
                TripState.EN_ROUTE_TO_PICKUP,
                TripState.RIDER_PICKED_UP,
                TripState.COMPLETED,
                TripState.PAID);
    }

    @Test
    @DisplayName("the PRICED event carries the quote and breakdown, not just the amount")
    void pricedEventCarriesTheQuote() throws Exception {
        UUID tripId = createTripExpectingPriced();

        TripEvent priced = drainEvents(tripId, 2).stream()
                .filter(event -> event.getToState() == TripState.PRICED)
                .findFirst()
                .orElseThrow();

        assertThat(priced.getPayload()).containsEntry("quotedPrice", 25.5);
        assertThat(priced.getPayload()).containsEntry("currency", "USD");
        assertThat(priced.getPayload()).containsKey("breakdown");
    }

    // ---------------------------------------------------------------------------------------
    // Acceptance criterion 2: no drivers available
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("Matching 404 (empty marketplace) ends the trip in UNMATCHED and releases surge demand")
    void noDriversAvailableEndsInUnmatched() throws Exception {
        STUBS.stub("/matches", Stub.status(404, "{\"message\":\"no drivers available\"}"));
        UUID tripId = createTripExpectingPriced();

        transition(tripId, TripState.ACCEPTED_BY_RIDER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("UNMATCHED"));

        assertThat(tripRepository.findById(tripId).orElseThrow().getState()).isEqualTo(TripState.UNMATCHED);
        // Leaving the pipeline unmatched must also stop counting the rider as waiting demand.
        assertThat(countRequests("DELETE", PENDING_REQUEST_PATH)).isEqualTo(1);

        assertThat(statesOf(drainEvents(tripId, 4))).containsExactly(
                TripState.REQUESTED, TripState.PRICED, TripState.ACCEPTED_BY_RIDER, TripState.UNMATCHED);
    }

    @Test
    @DisplayName("Matching 502 (outage) leaves the trip put and burns no attempt — never UNMATCHED")
    void matchingOutageDoesNotConsumeAnAttempt() throws Exception {
        STUBS.stub("/matches", Stub.status(502, "{\"message\":\"driver-discovery-service unavailable\"}"));
        UUID tripId = createTripExpectingPriced();

        mockMvc.perform(post("/trips/" + tripId + "/transition")
                        .contentType("application/json")
                        .content(transitionBody(TripState.ACCEPTED_BY_RIDER)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.dependency").value("matching-service"))
                .andExpect(jsonPath("$.tripId").value(tripId.toString()));

        TripEntity persisted = tripRepository.findById(tripId).orElseThrow();
        assertThat(persisted.getState()).isEqualTo(TripState.ACCEPTED_BY_RIDER);
        assertThat(persisted.getAttemptCount()).isZero();

        // Retrying once Matching recovers succeeds without having lost any of the k=3 budget.
        STUBS.stub("/matches", Stub.okJson(driverJson("driver-9")));
        mockMvc.perform(post("/trips/" + tripId + "/request-match"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DRIVER_PROPOSED"))
                .andExpect(jsonPath("$.driverId").value("driver-9"))
                .andExpect(jsonPath("$.attemptCount").value(0));
    }

    @Test
    @DisplayName("a client cannot assert an orchestrator-owned state such as DRIVER_PROPOSED")
    void clientCannotAssertOrchestratorOwnedStates() throws Exception {
        UUID tripId = createTripExpectingPriced();
        transition(tripId, TripState.ACCEPTED_BY_RIDER).andExpect(jsonPath("$.state").value("DRIVER_PROPOSED"));
        transition(tripId, TripState.DRIVER_ACCEPTED).andExpect(jsonPath("$.state").value("DRIVER_ACCEPTED"));

        // Without this guard a client could produce a DRIVER_PROPOSED trip with no driver on it.
        mockMvc.perform(post("/trips/" + tripId + "/transition")
                        .contentType("application/json")
                        .content(transitionBody(TripState.DRIVER_PROPOSED)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("request-match")));

        assertThat(tripRepository.findById(tripId).orElseThrow().getState())
                .isEqualTo(TripState.DRIVER_ACCEPTED);
    }

    // ---------------------------------------------------------------------------------------
    // Acceptance criterion 3: decline -> retry -> success
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("a decline retries matching, excludes the decliner, and succeeds with a different driver")
    void declineThenRetryThenSuccess() throws Exception {
        AtomicInteger matchCalls = new AtomicInteger();
        // Matching is deterministic, so without the exclusion list it would keep returning driver-1.
        // Here the stub honours the exclusion the way the real service does.
        STUBS.stubByQuery("/matches", query ->
                Stub.okJson(driverJson("driver-" + matchCalls.incrementAndGet())));

        UUID tripId = createTripExpectingPriced();

        transition(tripId, TripState.ACCEPTED_BY_RIDER)
                .andExpect(jsonPath("$.state").value("DRIVER_PROPOSED"))
                .andExpect(jsonPath("$.driverId").value("driver-1"));

        transition(tripId, TripState.DRIVER_DECLINED)
                .andExpect(status().isOk())
                // One request, two transitions: DRIVER_DECLINED then straight back to
                // DRIVER_PROPOSED with someone else.
                .andExpect(jsonPath("$.state").value("DRIVER_PROPOSED"))
                .andExpect(jsonPath("$.driverId").value("driver-2"))
                .andExpect(jsonPath("$.attemptCount").value(1))
                .andExpect(jsonPath("$.declinedDriverIds[0]").value("driver-1"));

        // The decline must be pushed to Matching, not just filtered locally: a deterministic
        // greedy matcher would otherwise re-propose driver-1 forever.
        List<StubServer.RecordedRequest> matchRequests = STUBS.requests().stream()
                .filter(request -> request.path().equals("/matches"))
                .toList();
        assertThat(matchRequests).hasSize(2);
        assertThat(matchRequests.get(0).body()).contains("\"excludedDriverIds\":[]");
        assertThat(matchRequests.get(1).body()).contains("\"excludedDriverIds\":[\"driver-1\"]");

        // The decliner stays excluded for the rest of the trip.
        transition(tripId, TripState.DRIVER_ACCEPTED).andExpect(jsonPath("$.state").value("DRIVER_ACCEPTED"));
        assertThat(tripRepository.findById(tripId).orElseThrow().getDeclinedDriverIds())
                .containsExactly("driver-1");

        assertThat(statesOf(drainEvents(tripId, 7))).containsExactly(
                TripState.REQUESTED,
                TripState.PRICED,
                TripState.ACCEPTED_BY_RIDER,
                TripState.DRIVER_PROPOSED,
                TripState.DRIVER_DECLINED,
                TripState.DRIVER_PROPOSED,
                TripState.DRIVER_ACCEPTED);
    }

    @Test
    @DisplayName("if Matching ignores the exclusions and re-proposes a decliner, we refuse the answer")
    void refusesAReProposedDecliner() throws Exception {
        // Simulates a Matching instance that predates excludedDriverIds: always the same driver.
        STUBS.stub("/matches", Stub.okJson(driverJson("driver-1")));

        UUID tripId = createTripExpectingPriced();
        transition(tripId, TripState.ACCEPTED_BY_RIDER).andExpect(jsonPath("$.driverId").value("driver-1"));

        // Rather than proposing driver-1 a second time, the trip ends honestly.
        transition(tripId, TripState.DRIVER_DECLINED)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("UNMATCHED"));

        assertThat(tripRepository.findById(tripId).orElseThrow().getDriverId()).isNull();
    }

    @Test
    @DisplayName("after k=3 declines the trip is UNMATCHED and matching is not called again")
    void exhaustingTheRetryBudgetEndsInUnmatched() throws Exception {
        AtomicInteger matchCalls = new AtomicInteger();
        STUBS.stubByQuery("/matches", query ->
                Stub.okJson(driverJson("driver-" + matchCalls.incrementAndGet())));

        UUID tripId = createTripExpectingPriced();
        transition(tripId, TripState.ACCEPTED_BY_RIDER).andExpect(jsonPath("$.state").value("DRIVER_PROPOSED"));

        transition(tripId, TripState.DRIVER_DECLINED).andExpect(jsonPath("$.state").value("DRIVER_PROPOSED"));
        transition(tripId, TripState.DRIVER_DECLINED).andExpect(jsonPath("$.state").value("DRIVER_PROPOSED"));
        transition(tripId, TripState.DRIVER_DECLINED)
                .andExpect(jsonPath("$.state").value("UNMATCHED"))
                .andExpect(jsonPath("$.attemptCount").value(3));

        // 3 matches: the initial one plus one per retry. The final decline spends the budget and
        // must not trigger a fourth.
        assertThat(STUBS.countRequestsTo("/matches")).isEqualTo(3);
        assertThat(tripRepository.findById(tripId).orElseThrow().getDeclinedDriverIds())
                .containsExactly("driver-1", "driver-2", "driver-3");
        assertThat(countRequests("DELETE", PENDING_REQUEST_PATH)).isEqualTo(1);
    }

    // ---------------------------------------------------------------------------------------
    // Pricing failure and the request-quote retry path
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("a pricing outage leaves the trip in REQUESTED and returns 502 with the trip id")
    void pricingFailureLeavesTripInRequested() throws Exception {
        STUBS.stub("/price-estimates", Stub.status(502, "{\"message\":\"route-service unavailable\"}"));
        // The in-memory database is shared by the whole class, so assert on the delta.
        long tripsBefore = tripRepository.count();

        String body = mockMvc.perform(post("/trips").contentType("application/json").content(CREATE_TRIP_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.dependency").value("price-estimation-service"))
                .andExpect(jsonPath("$.tripId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        UUID tripId = UUID.fromString(MAPPER.readTree(body).get("tripId").asText());
        assertThat(tripRepository.findById(tripId).orElseThrow().getState()).isEqualTo(TripState.REQUESTED);
        // Demand is only registered once a trip is actually priced and waiting.
        assertThat(countRequests("POST", PENDING_REQUEST_PATH)).isZero();

        // The documented retry path recovers the trip without creating a duplicate.
        STUBS.stub("/price-estimates", Stub.okJson(QUOTE_JSON));
        mockMvc.perform(post("/trips/" + tripId + "/request-quote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PRICED"))
                .andExpect(jsonPath("$.quotedPrice").value(25.50));

        assertThat(countRequests("POST", PENDING_REQUEST_PATH)).isEqualTo(1);
        // The retry recovered the existing trip rather than creating a second one.
        assertThat(tripRepository.count()).isEqualTo(tripsBefore + 1);
    }

    @Test
    @DisplayName("request-quote on an already priced trip is a 409, not a second quote")
    void requestQuoteOnAPricedTripIsRejected() throws Exception {
        UUID tripId = createTripExpectingPriced();

        mockMvc.perform(post("/trips/" + tripId + "/request-quote"))
                .andExpect(status().isConflict());

        // Rejected before calling Price Estimation again.
        assertThat(STUBS.countRequestsTo("/price-estimates")).isEqualTo(1);
        assertThat(countRequests("POST", PENDING_REQUEST_PATH)).isEqualTo(1);
    }

    @Test
    @DisplayName("a surge-pricing outage never fails a trip — the counter is best effort")
    void surgeOutageDoesNotFailTheTrip() throws Exception {
        STUBS.failConnection(PENDING_REQUEST_PATH);

        mockMvc.perform(post("/trips").contentType("application/json").content(CREATE_TRIP_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("PRICED"));
    }

    @Test
    @DisplayName("an unknown trip is a 404 and an invalid body a 400")
    void badRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/trips/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mockMvc.perform(post("/trips").contentType("application/json").content("{\"riderId\":\"\"}"))
                .andExpect(status().isBadRequest());
        assertThat(STUBS.countRequestsTo("/price-estimates")).isZero();
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private UUID createTripExpectingPriced() throws Exception {
        String body = mockMvc.perform(post("/trips").contentType("application/json").content(CREATE_TRIP_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("PRICED"))
                .andExpect(jsonPath("$.quotedPrice").value(25.50))
                .andExpect(jsonPath("$.quoteCurrency").value("USD"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(MAPPER.readTree(body).get("id").asText());
    }

    private org.springframework.test.web.servlet.ResultActions transition(UUID tripId, TripState toState)
            throws Exception {
        return mockMvc.perform(post("/trips/" + tripId + "/transition")
                .contentType("application/json")
                .content(transitionBody(toState)));
    }

    private static String transitionBody(TripState toState) {
        return "{\"toState\":\"" + toState + "\"}";
    }

    private static String driverJson(String driverId) {
        return "{\"driverId\":\"" + driverId + "\",\"location\":{\"lat\":37.78,\"lon\":-122.41},"
                + "\"etaSeconds\":180}";
    }

    private long countRequests(String method, String path) {
        return STUBS.requests().stream()
                .filter(request -> request.method().equals(method) && request.path().equals(path))
                .count();
    }

    private static List<TripState> statesOf(List<TripEvent> events) {
        return events.stream().map(TripEvent::getToState).toList();
    }

    /** Polls until {@code expected} events for this trip have arrived, or the timeout expires. */
    private List<TripEvent> drainEvents(UUID tripId, int expected) throws Exception {
        List<TripEvent> events = new ArrayList<>();
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(15).toMillis();
        while (events.size() < expected && System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                TripEvent event = MAPPER.readValue(record.value(), TripEvent.class);
                if (event.getTripId().equals(tripId.toString())) {
                    // Kafka keys events by tripId, so a trip's transitions stay ordered.
                    assertThat(record.key()).isEqualTo(tripId.toString());
                    events.add(event);
                }
            }
        }
        assertThat(events).hasSize(expected);
        return events;
    }

    private Consumer<String, String> createConsumer() {
        var props = KafkaTestUtils.consumerProps(
                "trip-orchestration-it-" + UUID.randomUUID(), "false", embeddedKafka);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
    }
}







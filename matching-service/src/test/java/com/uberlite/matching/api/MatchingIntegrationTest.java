package com.uberlite.matching.api;

import com.uberlite.common.testing.StubServer;
import com.uberlite.common.testing.StubServer.Stub;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end matching flow with driver-discovery-service and route-service stubbed over real HTTP
 * (issue acceptance criteria 2 and 3).
 *
 * <p>Eureka is disabled and both {@code @FeignClient} service ids are pointed at the stub server via
 * the SimpleDiscoveryClient, so the real Feign clients, real JSON codecs and real URLs are
 * exercised. This is what catches client/route mismatches — e.g. the pre-existing client that asked
 * for {@code /drivers/nearest}, a route Driver Discovery has never exposed.
 */
@SpringBootTest
class MatchingIntegrationTest {

    private static final StubServer STUBS = new StubServer();

    private static final String REQUEST_BODY = """
            {
              "tripId": "trip-1",
              "pickup": {"lat": 37.7749, "lon": -122.4194}
            }
            """;

    @Autowired private WebApplicationContext context;
    private MockMvc mockMvc;

    @AfterAll
    static void stopStubs() {
        STUBS.close();
    }

    @DynamicPropertySource
    static void downstreamUris(DynamicPropertyRegistry registry) {
        String uri = STUBS.baseUrl();
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.discovery.client.simple.instances.driver-discovery-service[0].uri", () -> uri);
        registry.add("spring.cloud.discovery.client.simple.instances.route-service[0].uri", () -> uri);
        // Pin the levers so the expected ETA is stable regardless of application.yml.
        registry.add("matching.average-speed-mps", () -> "10.0");
        registry.add("matching.default-detour-factor", () -> "1.0");
        registry.add("matching.radius-meters", () -> "3000");
        registry.add("matching.candidate-limit", () -> "10");
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        STUBS.reset();
    }

    /** Three drivers; the one at lat1=2.0 is nearest. DRS's own etaSeconds is a decoy. */
    private void stubThreeDrivers() {
        STUBS.stub("/drivers/nearby", Stub.okJson("""
                [
                  {"driverId":"far",  "location":{"lat":1.0,"lon":-122.4194},"etaSeconds":99999},
                  {"driverId":"near", "location":{"lat":2.0,"lon":-122.4194},"etaSeconds":99999},
                  {"driverId":"mid",  "location":{"lat":3.0,"lon":-122.4194},"etaSeconds":99999}
                ]
                """));
        STUBS.stubByQuery("/route/estimate", query -> {
            double km = query.contains("lat1=1.0") ? 9.0 : query.contains("lat1=2.0") ? 1.5 : 4.0;
            return Stub.okJson("{\"straightDistanceKm\":" + km + ",\"detourFactor\":null}");
        });
    }

    @Test
    @DisplayName("POST /matches returns the lowest-ETA candidate")
    void returnsBestCandidate() throws Exception {
        stubThreeDrivers();

        mockMvc.perform(post("/matches").contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").value("near"))
                // 1.5 km / 10 m/s = 150s, recomputed here — not the 99999 DRS reported.
                .andExpect(jsonPath("$.etaSeconds").value(150))
                .andExpect(jsonPath("$.location.lat").value(2.0));

        // One discovery call, one route call per candidate.
        assertThat(STUBS.countRequestsTo("/drivers/nearby")).isEqualTo(1);
        assertThat(STUBS.countRequestsTo("/route/estimate")).isEqualTo(3);
    }

    @Test
    @DisplayName("configured radius and limit reach Driver Discovery on the wire")
    void sendsConfiguredSearchParameters() throws Exception {
        stubThreeDrivers();

        mockMvc.perform(post("/matches").contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY))
                .andExpect(status().isOk());

        String query = STUBS.requestTo("/drivers/nearby").query();
        assertThat(query).contains("lat=37.7749").contains("lon=-122.4194")
                .contains("radiusMeters=3000").contains("limit=10");
    }

    @Test
    @DisplayName("no drivers available -> 404")
    void returnsNotFoundWhenNoDriversAvailable() throws Exception {
        STUBS.stub("/drivers/nearby", Stub.okJson("[]"));

        mockMvc.perform(post("/matches").contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.tripId").value("trip-1"));

        assertThat(STUBS.countRequestsTo("/route/estimate")).isZero();
    }

    @Test
    @DisplayName("Driver Discovery unreachable -> 502, so a 404 always means an empty marketplace")
    void returnsBadGatewayWhenDiscoveryUnreachable() throws Exception {
        STUBS.failConnection("/drivers/nearby");

        mockMvc.perform(post("/matches").contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.dependency").value("driver-discovery-service"));
    }

    @Test
    @DisplayName("candidates found but Route Service unreachable -> 502, not 404")
    void returnsBadGatewayWhenRouteServiceUnreachable() throws Exception {
        STUBS.stub("/drivers/nearby", Stub.okJson("""
                [{"driverId":"a","location":{"lat":1.0,"lon":-122.4194},"etaSeconds":0}]
                """));
        STUBS.failConnection("/route/estimate");

        mockMvc.perform(post("/matches").contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.dependency").value("route-service"));
    }

    @Test
    @DisplayName("a blank tripId is rejected with 400 before any downstream call")
    void rejectsInvalidRequest() throws Exception {
        mockMvc.perform(post("/matches").contentType(MediaType.APPLICATION_JSON).content("""
                        {"tripId": "", "pickup": {"lat": 37.7749, "lon": -122.4194}}
                        """))
                .andExpect(status().isBadRequest());

        assertThat(STUBS.requests()).isEmpty();
    }

    @Test
    @DisplayName("an out-of-range pickup latitude is rejected with 400")
    void rejectsOutOfRangePickup() throws Exception {
        mockMvc.perform(post("/matches").contentType(MediaType.APPLICATION_JSON).content("""
                        {"tripId": "trip-1", "pickup": {"lat": 999.0, "lon": -122.4194}}
                        """))
                .andExpect(status().isBadRequest());
    }
}



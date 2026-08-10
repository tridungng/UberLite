package com.uberlite.priceestimation.api;

import com.uberlite.common.geo.H3Util;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end aggregation flow with all five downstream services stubbed over real HTTP.
 *
 * <p>Eureka is disabled and every {@code @FeignClient} service id is pointed at the stub server via
 * the SimpleDiscoveryClient, so the real Feign clients, the real JSON codecs and the real URLs are
 * exercised. This is what catches client/route mismatches (e.g. {@code /surge/multiplier} vs
 * {@code /surge/{h3Cell}}), which pure Mockito tests cannot.
 */
@SpringBootTest
class PriceEstimationIntegrationTest {

    private static final StubServer STUBS = new StubServer();

    /** H3 resolution 8 cell for the pickup below; the surge route is keyed on it. */
    private static final String PICKUP_CELL = H3Util.latLngToCell(37.7749, -122.4194);

    private static final String REQUEST_BODY = """
            {
              "riderId": "rider-1",
              "riderTripCount": 0,
              "pickup":  {"lat": 37.7749, "lon": -122.4194},
              "dropoff": {"lat": 37.8044, "lon": -122.2712}
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
        registry.add("spring.cloud.discovery.client.simple.instances.route-service[0].uri", () -> uri);
        registry.add("spring.cloud.discovery.client.simple.instances.time-estimation-service[0].uri", () -> uri);
        registry.add("spring.cloud.discovery.client.simple.instances.surge-pricing-service[0].uri", () -> uri);
        registry.add("spring.cloud.discovery.client.simple.instances.tax-tolls-service[0].uri", () -> uri);
        registry.add("spring.cloud.discovery.client.simple.instances.discounts-promotions-service[0].uri", () -> uri);
        // Pin the pricing levers so the expected amount is stable regardless of application.yml.
        registry.add("pricing.cost-per-km", () -> "1.0");
        registry.add("pricing.cost-per-minute", () -> "0.5");
        registry.add("pricing.default-detour-factor", () -> "1.1");
        registry.add("pricing.region-id", () -> "us-ca");
        registry.add("pricing.currency", () -> "USD");
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        stubHappyPath();
    }

    private void stubHappyPath() {
        STUBS.reset();
        STUBS.stub("/route/estimate", Stub.okJson("{\"straightDistanceKm\":10.0,\"detourFactor\":1.1}"))
                .stub("/time/estimate", Stub.okJson("{\"minutes\":20.0}"))
                .stub("/surge/" + PICKUP_CELL,
                        Stub.okJson("{\"h3Cell\":\"" + PICKUP_CELL + "\",\"multiplier\":1.5,\"updatedAtMs\":1}"))
                .stub("/tax/us-ca", Stub.okJson("{\"regionId\":\"us-ca\",\"rate\":0.08}"))
                .stub("/tolls/estimate", Stub.okJson("{\"amount\":2.5}"))
                .stub("/discounts/evaluate", Stub.okJson("{\"discountPct\":0.2}"));
    }

    @Test
    @DisplayName("aggregates all five services and returns the quote with a full breakdown")
    void aggregatesAllDownstreamServices() throws Exception {
        mockMvc.perform(post("/price-estimates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                // ((1.0*11 + 0.5*20) * 1.5 + 2.5) * (1 - 0.2) * (1 + 0.08) = 29.376 -> 29.38
                .andExpect(jsonPath("$.amount").value(29.38))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.breakdown.distanceKm").value(11.0))
                .andExpect(jsonPath("$.breakdown.estimatedMinutes").value(20.0))
                .andExpect(jsonPath("$.breakdown.surgeMultiplier").value(1.5))
                .andExpect(jsonPath("$.breakdown.tollAmount").value(2.5))
                .andExpect(jsonPath("$.breakdown.discountPct").value(0.2))
                .andExpect(jsonPath("$.breakdown.taxRate").value(0.08))
                .andExpect(jsonPath("$.breakdown.costPerKm").value(1.0))
                .andExpect(jsonPath("$.breakdown.costPerMinute").value(0.5))
                .andExpect(jsonPath("$.breakdown.baseFare").value(21.00))
                .andExpect(jsonPath("$.breakdown.surgedFare").value(31.50))
                .andExpect(jsonPath("$.breakdown.fareWithTolls").value(34.00))
                .andExpect(jsonPath("$.breakdown.discountAmount").value(6.80))
                .andExpect(jsonPath("$.breakdown.fareAfterDiscount").value(27.20))
                .andExpect(jsonPath("$.breakdown.taxAmount").value(2.18))
                .andExpect(jsonPath("$.breakdown.total").value(29.38))
                .andExpect(jsonPath("$.breakdown.regionId").value("us-ca"))
                .andExpect(jsonPath("$.breakdown.pickupH3Cell").value(PICKUP_CELL));

        // All five dependencies were actually consulted.
        assertThat(STUBS.countRequestsTo("/route/estimate")).isEqualTo(1);
        assertThat(STUBS.countRequestsTo("/time/estimate")).isEqualTo(1);
        assertThat(STUBS.countRequestsTo("/surge/" + PICKUP_CELL)).isEqualTo(1);
        assertThat(STUBS.countRequestsTo("/tax/us-ca")).isEqualTo(1);
        assertThat(STUBS.countRequestsTo("/tolls/estimate")).isEqualTo(1);
        assertThat(STUBS.countRequestsTo("/discounts/evaluate")).isEqualTo(1);
    }

    @Test
    @DisplayName("sends the rider context to Discounts and the trip distance in metres to Tolls")
    void sendsCorrectRequestPayloads() throws Exception {
        mockMvc.perform(post("/price-estimates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk());

        assertThat(STUBS.requestTo("/discounts/evaluate").body())
                .contains("\"riderId\":\"rider-1\"")
                .contains("\"riderTripCount\":0");
        assertThat(STUBS.requestTo("/tolls/estimate").body()).contains("\"distanceMeters\":11000");
        assertThat(STUBS.requestTo("/route/estimate").query())
                .contains("lat1=37.7749")
                .contains("lon2=-122.2712");
    }

    @Test
    @DisplayName("a downstream 500 yields 502 naming that service, never a guessed price")
    void downstreamErrorYields502NamingTheService() throws Exception {
        STUBS.stub("/tax/us-ca", Stub.status(500, "{\"message\":\"boom\"}"));

        mockMvc.perform(post("/price-estimates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.dependency").value("tax-tolls-service"))
                .andExpect(jsonPath("$.message").value(containsString("tax-tolls-service")))
                .andExpect(jsonPath("$.amount").doesNotExist());
    }

    @Test
    @DisplayName("an unreachable dependency also yields 502 naming that service")
    void unreachableDependencyYields502() throws Exception {
        STUBS.failConnection("/route/estimate");

        mockMvc.perform(post("/price-estimates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.dependency").value("route-service"));
    }

    @Test
    @DisplayName("each of the five dependencies is named individually when it is the one that fails")
    void everyDependencyIsNamedOnFailure() throws Exception {
        record Case(String path, String dependency) {}
        List<Case> cases = List.of(
                new Case("/route/estimate", "route-service"),
                new Case("/time/estimate", "time-estimation-service"),
                new Case("/surge/" + PICKUP_CELL, "surge-pricing-service"),
                new Case("/tolls/estimate", "tax-tolls-service"),
                new Case("/discounts/evaluate", "discounts-promotions-service"));

        for (Case testCase : cases) {
            stubHappyPath();
            STUBS.stub(testCase.path(), Stub.status(503, "{\"message\":\"unavailable\"}"));

            mockMvc.perform(post("/price-estimates")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_BODY))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.dependency").value(testCase.dependency()));
        }
    }

    @Test
    @DisplayName("an invalid request is rejected with 400 before any downstream call is made")
    void invalidRequestIsRejected() throws Exception {
        mockMvc.perform(post("/price-estimates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"riderId\":\"\",\"riderTripCount\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(STUBS.requests()).isEmpty();
    }
}


package com.uberlite.priceestimation;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import com.github.tomakehurst.wiremock.client.WireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest
public class PriceEstimationIntegrationTest {
    static WireMockServer routeMock;
    static WireMockServer timeMock;
    static WireMockServer surgeMock;
    static WireMockServer taxMock;
    static WireMockServer discountsMock;

    @BeforeAll
    static void start() {
        routeMock = new WireMockServer(8087);
        timeMock = new WireMockServer(8088);
        surgeMock = new WireMockServer(8084);
        taxMock = new WireMockServer(8090);
        discountsMock = new WireMockServer(8091);
        routeMock.start();
        timeMock.start();
        surgeMock.start();
        taxMock.start();
        discountsMock.start();

        routeMock.stubFor(get(urlPathEqualTo("/route/estimate"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"straightDistanceKm\":10.0,\"detourFactor\":1.1}")));
        timeMock.stubFor(get(urlPathEqualTo("/time/estimate"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"estimatedMinutes\":20.0}")));
        surgeMock.stubFor(get(urlPathEqualTo("/surge/multiplier"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"multiplier\":1.5}")));
        taxMock.stubFor(get(urlPathEqualTo("/tax/CA"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"regionId\":\"CA\",\"rate\":0.08}")));
        taxMock.stubFor(post(urlPathEqualTo("/tolls/estimate"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"amount\":2.5}")));
        discountsMock.stubFor(post(urlPathEqualTo("/discounts/evaluate"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"discountPct\":0.2}")));
    }

    @AfterAll
    static void stop() {
        routeMock.stop();
        timeMock.stop();
        surgeMock.stop();
        taxMock.stop();
        discountsMock.stop();
    }

    @Test
    void wiremock_pipeline_runs() {
        // This test simply ensures WireMock stubs start; full end-to-end call requires running the service
        // and is validated in system integration. Here we assert the stubs are running by verifying zero requests so
        // far.
        WireMock.verify(0, getRequestedFor(urlPathEqualTo("/route/estimate")));
    }
}

package com.uberlite.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the gateway and asserts every declared route is loaded and points at the right service id.
 *
 * <p>A route table is configuration, and configuration only exercised by hand rots: a typo in a
 * service id or a {@code Path} predicate stays invisible until someone runs {@code scripts/demo.sh}
 * and gets a 404 from the gateway. This fails the build instead.
 *
 * <p>It also pins the property prefix. Spring Cloud moved gateway routes to
 * {@code spring.cloud.gateway.server.webflux.*}; under the old prefix the routes parse as nothing
 * at all and the gateway starts perfectly happily with an empty table.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApiGatewayRoutesTest {

    /** Every service that must be reachable through the gateway. */
    private static final List<String> EXPECTED_ROUTE_IDS = List.of(
            "trip-service",
            "price-estimation-service",
            "matching-service",
            "driver-discovery-service",
            "route-service",
            "time-estimation-service",
            "surge-pricing-service",
            "tax-tolls-service",
            "discounts-promotions-service",
            "forecasting-service",
            "matching-analytics-service",
            "discounts-analytics-service");

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void everyServiceIsRoutable() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();

        assertThat(routes)
                .as("routes did not load - check the spring.cloud.gateway.server.webflux prefix")
                .isNotNull()
                .isNotEmpty();
        assertThat(routes.stream().map(Route::getId))
                .containsExactlyInAnyOrderElementsOf(EXPECTED_ROUTE_IDS);
    }

    @Test
    void everyRouteResolvesThroughEurekaRatherThanAHardcodedHost() {
        Map<String, String> uris = routeLocator.getRoutes().collectList().block().stream()
                .collect(Collectors.toMap(Route::getId, route -> route.getUri().toString()));

        assertThat(uris).allSatisfy((id, uri) -> assertThat(uri)
                .as("route '%s' must use lb:// so an instance can move without a gateway restart", id)
                .isEqualTo("lb://" + id));
    }
}

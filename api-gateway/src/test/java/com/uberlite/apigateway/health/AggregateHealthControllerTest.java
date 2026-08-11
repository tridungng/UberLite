package com.uberlite.apigateway.health;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the aggregate health fan-out.
 *
 * <p>Real HTTP against throwaway {@link HttpServer}s rather than a mocked {@code WebClient}: the
 * behaviour worth protecting is how this endpoint reacts to a downstream that answers slowly,
 * answers DOWN, or is not there at all, and a stubbed client exercises none of it. One server per
 * simulated instance, because a Eureka {@code ServiceInstance} is a host/port pair with no base
 * path to share.
 */
class AggregateHealthControllerTest {

    private static final Duration TIMEOUT = Duration.ofMillis(300);
    private static final String UP_BODY = "{\"status\":\"UP\"}";
    private static final String DOWN_BODY = "{\"status\":\"DOWN\"}";

    private final Map<String, List<ServiceInstance>> registry = new LinkedHashMap<>();
    private final List<HttpServer> servers = new ArrayList<>();

    private final AggregateHealthController controller = new AggregateHealthController(
            new StaticDiscoveryClient(registry), WebClient.builder().build(),
            new AggregateHealthProperties(TIMEOUT));

    @AfterEach
    void tearDown() {
        servers.forEach(server -> server.stop(0));
    }

    @Test
    void reportsUpAndReturns200WhenEveryInstanceIsUp() {
        register("trip-service", instance(200, UP_BODY, Duration.ZERO));
        register("route-service", instance(200, UP_BODY, Duration.ZERO));

        ResponseEntity<AggregateHealthReport> response = call();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(AggregateHealthReport.UP);
        assertThat(response.getBody().services()).containsOnlyKeys("route-service", "trip-service");
    }

    @Test
    void servicesAreOrderedAlphabeticallySoTheReportDiffsCleanly() {
        register("trip-service", instance(200, UP_BODY, Duration.ZERO));
        register("api-gateway", instance(200, UP_BODY, Duration.ZERO));
        register("matching-service", instance(200, UP_BODY, Duration.ZERO));

        assertThat(call().getBody().services().keySet())
                .containsExactly("api-gateway", "matching-service", "trip-service");
    }

    @Test
    void oneDownServiceMakesTheWholeReportDownWith503() {
        register("trip-service", instance(200, UP_BODY, Duration.ZERO));
        register("route-service", instance(503, DOWN_BODY, Duration.ZERO));

        ResponseEntity<AggregateHealthReport> response = call();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().status()).isEqualTo(AggregateHealthReport.DOWN);
        assertThat(response.getBody().services().get("trip-service").status())
                .isEqualTo(AggregateHealthReport.UP);
        assertThat(response.getBody().services().get("route-service").status())
                .isEqualTo(AggregateHealthReport.DOWN);
    }

    @Test
    void anUnreachableInstanceIsReportedDownWithItsErrorRatherThanFailingTheCall() {
        // Registered but nothing is listening: the stale-registration case Eureka produces when a
        // container dies between heartbeats.
        registry.put("ghost-service", List.of(
                new DefaultServiceInstance("ghost-1", "ghost-service", "127.0.0.1", 1, false)));

        AggregateHealthReport.InstanceHealth instance =
                call().getBody().services().get("ghost-service").instances().getFirst();

        assertThat(instance.status()).isEqualTo(AggregateHealthReport.DOWN);
        assertThat(instance.error()).isNotBlank();
    }

    @Test
    void anInstanceSlowerThanTheTimeoutIsDownRatherThanStallingTheReport() {
        register("slow-service", instance(200, UP_BODY, TIMEOUT.multipliedBy(10)));

        ResponseEntity<AggregateHealthReport> response = call();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().services().get("slow-service").instances().getFirst().error())
                .contains("Timeout");
    }

    @Test
    void aPartiallyDownServiceIsDownSoAnInstanceLevelOutageIsNotAveragedAway() {
        register("trip-service",
                instance(200, UP_BODY, Duration.ZERO),
                instance(503, DOWN_BODY, Duration.ZERO));

        AggregateHealthReport.ServiceHealth trip = call().getBody().services().get("trip-service");

        assertThat(trip.status()).isEqualTo(AggregateHealthReport.DOWN);
        assertThat(trip.instances()).hasSize(2);
    }

    @Test
    void anEmptyRegistryIsDownBecauseNothingRunningMustNotReadAsHealthy() {
        ResponseEntity<AggregateHealthReport> response = call();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().services()).isEmpty();
    }

    private ResponseEntity<AggregateHealthReport> call() {
        return controller.aggregate().block(Duration.ofSeconds(10));
    }

    private void register(String serviceId, int... ports) {
        List<ServiceInstance> instances = new ArrayList<>();
        for (int i = 0; i < ports.length; i++) {
            instances.add(new DefaultServiceInstance(
                    serviceId + "-" + i, serviceId, "127.0.0.1", ports[i], false));
        }
        registry.put(serviceId, instances);
    }

    /** Starts a server that serves one health document at {@code /actuator/health}. */
    private int instance(int status, String body, Duration delay) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servers.add(server);
            server.createContext("/actuator/health", exchange -> {
                sleep(delay);
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, payload.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(payload);
                }
            });
            server.start();
            return server.getAddress().getPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void sleep(Duration delay) {
        if (delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Minimal {@link ReactiveDiscoveryClient} over an in-memory registry. */
    private record StaticDiscoveryClient(Map<String, List<ServiceInstance>> registry)
            implements ReactiveDiscoveryClient {

        @Override
        public String description() {
            return "static";
        }

        @Override
        public Flux<ServiceInstance> getInstances(String serviceId) {
            return Flux.fromIterable(registry.getOrDefault(serviceId, List.of()));
        }

        @Override
        public Flux<String> getServices() {
            return Flux.fromIterable(registry.keySet());
        }
    }
}


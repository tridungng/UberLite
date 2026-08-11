package com.uberlite.apigateway.health;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.uberlite.apigateway.health.AggregateHealthReport.DOWN;
import static com.uberlite.apigateway.health.AggregateHealthReport.InstanceHealth;
import static com.uberlite.apigateway.health.AggregateHealthReport.ServiceHealth;
import static com.uberlite.apigateway.health.AggregateHealthReport.UP;

/**
 * Aggregate health view over every service registered with Eureka (issue 11).
 *
 * <p>Answers "is the whole system up?" in one call, which otherwise means visiting fourteen
 * {@code /actuator/health} URLs by hand. The service list is taken from the registry rather than
 * hardcoded, so a service added later shows up here without touching this class.
 *
 * <p>Deliberately <em>not</em> a {@code HealthIndicator} contributing to the gateway's own
 * {@code /actuator/health}: the gateway's health must describe the gateway. Folding downstream
 * status into it would make Compose restart a perfectly healthy gateway because an unrelated
 * analytics service was still booting.
 */
@RestController
public class AggregateHealthController {

    private static final String HEALTH_PATH = "/actuator/health";

    private final ReactiveDiscoveryClient discoveryClient;
    private final WebClient webClient;
    private final AggregateHealthProperties properties;

    public AggregateHealthController(ReactiveDiscoveryClient discoveryClient,
                                     WebClient healthWebClient,
                                     AggregateHealthProperties properties) {
        this.discoveryClient = discoveryClient;
        this.webClient = healthWebClient;
        this.properties = properties;
    }

    /**
     * @return {@code 200} when every registered instance is {@code UP}, {@code 503} otherwise, so
     *     the endpoint is usable from a script with nothing but the exit code of {@code curl -f}
     */
    @GetMapping("/health/aggregate")
    public Mono<ResponseEntity<AggregateHealthReport>> aggregate() {
        return discoveryClient.getServices()
                .flatMap(serviceId -> checkService(serviceId).map(health -> Map.entry(serviceId, health)))
                .collectList()
                .map(AggregateHealthController::toReport)
                .map(AggregateHealthController::toResponse);
    }

    private Mono<ServiceHealth> checkService(String serviceId) {
        return discoveryClient.getInstances(serviceId)
                .flatMap(this::checkInstance)
                .collectList()
                .map(instances -> new ServiceHealth(rollUp(instances, InstanceHealth::status), instances));
    }

    private Mono<InstanceHealth> checkInstance(ServiceInstance instance) {
        URI uri = instance.getUri();
        return webClient.get()
                .uri(uri.resolve(HEALTH_PATH))
                .retrieve()
                .bodyToMono(HealthBody.class)
                .timeout(properties.timeout())
                .map(body -> new InstanceHealth(uri.toString(), body.statusOrDown(), null))
                // An instance that is registered but unreachable is DOWN, not an error for the
                // caller to handle: reporting it is the entire point of this endpoint.
                .onErrorResume(e -> Mono.just(
                        new InstanceHealth(uri.toString(), DOWN, e.getClass().getSimpleName() + ": " + e.getMessage())));
    }

    private static AggregateHealthReport toReport(List<Map.Entry<String, ServiceHealth>> entries) {
        Map<String, ServiceHealth> services = new LinkedHashMap<>();
        entries.stream()
                // Stable, alphabetical output: this gets eyeballed and diffed by humans.
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> services.put(entry.getKey(), entry.getValue()));
        return new AggregateHealthReport(rollUp(List.copyOf(services.values()), ServiceHealth::status), services);
    }

    private static ResponseEntity<AggregateHealthReport> toResponse(AggregateHealthReport report) {
        return ResponseEntity.status(UP.equals(report.status()) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(report);
    }

    /** An empty registry is DOWN, not UP: "nothing is running" must never read as healthy. */
    private static <T> String rollUp(List<T> items, Function<T, String> status) {
        return !items.isEmpty() && items.stream().allMatch(item -> UP.equals(status.apply(item))) ? UP : DOWN;
    }

    /** Only the {@code status} field of Actuator's health document matters here. */
    private record HealthBody(String status) {
        String statusOrDown() {
            return status == null ? DOWN : status;
        }
    }
}


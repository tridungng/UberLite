package com.uberlite.apigateway.health;

import java.util.List;
import java.util.Map;

/**
 * The aggregate health report served by {@link AggregateHealthController}.
 *
 * @param status   {@code UP} only when every discovered instance is {@code UP}
 * @param services per-service status, keyed by Eureka service id
 */
public record AggregateHealthReport(String status, Map<String, ServiceHealth> services) {

    /** Status strings are Actuator's, so this endpoint reads the same as a per-service check. */
    public static final String UP = "UP";
    public static final String DOWN = "DOWN";

    /**
     * @param status    {@code UP} only when every instance of this service is {@code UP}
     * @param instances one entry per registered instance, so a partial outage is visible rather
     *                  than averaged away
     */
    public record ServiceHealth(String status, List<InstanceHealth> instances) {
    }

    /**
     * @param uri    the instance's base URI as registered with Eureka
     * @param status the instance's own {@code /actuator/health} status
     * @param error  why the check failed, or {@code null} when it succeeded
     */
    public record InstanceHealth(String uri, String status, String error) {
    }
}


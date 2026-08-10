package com.uberlite.common.events.kafka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Shared consumer policy for {@code trip-events}.
 *
 * <p>Lives in {@code common} because it is a decision all three subscribers have to make the same
 * way, not merely because it is convenient: they are all background analytics reading one
 * partitioned stream, and a divergent retry policy in one of them would surface as an unexplained
 * gap in a report rather than as a failure.
 *
 * <p>The <em>deserializer</em> is deliberately not configured here. It is set through Boot's own
 * {@code spring.kafka.consumer.*} properties in each service's {@code application.yml}, which keeps
 * this module off the Jackson 3 classpath that {@code spring-kafka} 4's serializers require. Two
 * settings are load-bearing and are commented where they appear:
 * {@code spring.json.use.type.headers=false}, because Trip Service publishes with
 * {@code noTypeInfo()} and sends no {@code __TypeId__} header, and
 * {@code spring.json.value.default.type}, which names {@code TripEvent} in its place.
 *
 * <p>Services opt in with {@code @Import(TripEventConsumerConfiguration.class)}; Boot's
 * auto-configured listener container factory picks the error handler up automatically.
 */
@Configuration
public class TripEventConsumerConfiguration {

    /**
     * Retries a failing record a few times, then logs it and moves on.
     *
     * <p>Spring's default retries forever, which is the wrong trade for these consumers: they are
     * analytics, and a single poison message must not block its partition — and with it every later
     * event — indefinitely. Dropping one record costs a row in a report; stalling costs the whole
     * report from that moment on. A dead-letter topic is the production answer and is deliberately
     * out of MVP scope (ARCHITECTURE.md Sec. 5 lists no DLQ).
     */
    @Bean
    public CommonErrorHandler tripEventErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1_000L, 2L));
    }
}
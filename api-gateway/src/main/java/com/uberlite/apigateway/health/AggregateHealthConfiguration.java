package com.uberlite.apigateway.health;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Wiring for the aggregate health view.
 *
 * <p>The {@link WebClient} is declared here rather than injected from an ambient
 * {@code WebClient.Builder}: Boot does not auto-configure a builder bean in this application, and
 * more importantly this client wants its own settings. It talks to instances the registry may
 * already consider dead, so it must never inherit a caller-facing default.
 */
@Configuration(proxyBeanMethods = false)
class AggregateHealthConfiguration {

    /**
     * Not {@code @LoadBalanced}: {@link AggregateHealthController} addresses each instance
     * individually on purpose, because "3 of 4 instances up" is the interesting answer and load
     * balancing hides it.
     */
    @Bean
    WebClient healthWebClient() {
        return WebClient.builder().build();
    }
}


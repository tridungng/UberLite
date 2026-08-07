package com.uberlite.driverdiscovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Spring Boot application entry for the Driver Discovery service.
 * <p>
 * Exposes infrastructure beans used across the module and enables scheduling.
 */
@EnableScheduling
@SpringBootApplication
public class DriverDiscoveryApplication {
    /**
     * Application entry point. Starts the Spring context for this service.
     * <p>
     * Kept package-private for consistency with other modules' bootstraps.
     *
     * @param args runtime arguments passed to SpringApplication
     */
    static void main(String[] args) {
        SpringApplication.run(DriverDiscoveryApplication.class, args);
    }

    /**
     * Provide a Clock instance wired to UTC. Use Clock injection to make code
     * testable and avoid calls to system time directly.
     *
     * @return system UTC clock
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

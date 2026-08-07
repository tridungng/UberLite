package com.uberlite.driverdiscovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@EnableScheduling
@SpringBootApplication
public class DriverDiscoveryApplication {
    public static void main(String[] args) { SpringApplication.run(DriverDiscoveryApplication.class, args); }

    @Bean
    public Clock clock() { return Clock.systemUTC(); }
}

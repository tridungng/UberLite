package com.uberlite.discountsanalytics;

import com.uberlite.discountsanalytics.config.PromoBatchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
@EnableFeignClients
@EnableConfigurationProperties(PromoBatchProperties.class)
public class DiscountsAnalyticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscountsAnalyticsServiceApplication.class, args);
    }

    /** Injected so a test can assert what {@code flagged_at} was stamped with. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

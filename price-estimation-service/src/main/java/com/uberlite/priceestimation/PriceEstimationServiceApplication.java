package com.uberlite.priceestimation;

import com.uberlite.priceestimation.config.PricingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
@EnableConfigurationProperties(PricingProperties.class)
public class PriceEstimationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PriceEstimationServiceApplication.class, args);
    }
}

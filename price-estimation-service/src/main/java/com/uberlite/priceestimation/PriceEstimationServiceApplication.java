package com.uberlite.priceestimation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class PriceEstimationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PriceEstimationServiceApplication.class, args);
    }
}

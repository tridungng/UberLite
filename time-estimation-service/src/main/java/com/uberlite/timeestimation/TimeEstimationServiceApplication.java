package com.uberlite.timeestimation;

import com.uberlite.timeestimation.config.TimeEstimationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TimeEstimationProperties.class)
public class TimeEstimationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TimeEstimationServiceApplication.class, args);
    }
}

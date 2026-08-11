package com.uberlite.apigateway;

import com.uberlite.apigateway.health.AggregateHealthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AggregateHealthProperties.class)
public class ApiGatewayApplication {
    static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}

package com.uberlite.taxtolls;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.flywaydb.core.Flyway;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
public class TaxTollsIntegrationTest {

    @Container
    public static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("taxtollsdb")
            .withUsername("uberlite")
            .withPassword("changeme");

    @DynamicPropertySource
    static void datasourceProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    @Test
    void seeded_tax_rate_available() {
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                "http://localhost:" + port + "/tax/CA",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                }
        );
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> body = resp.getBody();
        assertThat(body).containsEntry("regionId", "CA");
        assertThat(((Number) body.get("rate")).doubleValue()).isEqualTo(0.0725);
    }
}

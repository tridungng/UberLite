package com.uberlite.taxtolls;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class TaxTollsIntegrationTest {

    @Container
    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("taxtollsdb")
            .withUsername("uberlite")
            .withPassword("changeme");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void seeded_tax_rate_available() {
        ResponseEntity<Map> resp = restTemplate.getForEntity("/tax/CA", Map.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        Map body = resp.getBody();
        assertThat(body).containsEntry("regionId", "CA");
        assertThat(body).containsKey("rate");
    }
}

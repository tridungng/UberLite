package com.uberlite.driverdiscovery;

import com.uberlite.common.dto.DriverCandidateDto;
import com.uberlite.common.dto.LocationDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class DriverDiscoveryIntegrationTest {
    @Container
    public static GenericContainer<?> redis = new GenericContainer<>("redis:7.2").withExposedPorts(6379).waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> redis.getMappedPort(6379));
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Test
    public void nearbyOrdering() throws Exception {
        String base = "http://localhost:" + port;
        rest.postForEntity(base + "/drivers/d1/location", new LocationDto(37.7749, -122.4194), Void.class);
        rest.postForEntity(base + "/drivers/d2/location", new LocationDto(37.7758, -122.4183), Void.class);
        ResponseEntity<DriverCandidateDto[]> resp = rest.getForEntity(base + "/drivers/nearby?lat=37.7753&lon=-122.4189&radiusMeters=2000&limit=10", DriverCandidateDto[].class);
        DriverCandidateDto[] arr = resp.getBody();
        // Expect the closer driver first (d1)
        assertEquals("d1", arr[0].getDriverId());
    }
}

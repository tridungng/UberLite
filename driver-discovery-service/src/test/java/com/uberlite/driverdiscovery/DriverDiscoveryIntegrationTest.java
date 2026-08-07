package com.uberlite.driverdiscovery;

import com.uberlite.common.dto.DriverCandidateDto;
import com.uberlite.common.dto.LocationDto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
public class DriverDiscoveryIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.2").withExposedPorts(6379).waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);

        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @LocalServerPort
    int port;

    RestTemplate rest = new RestTemplate();

    @Autowired
    Environment environment;

    @Autowired
    RedisConnectionFactory redisConnectionFactory;

    @Test
    void debugRedisConnectionFactory() {
        if (redisConnectionFactory instanceof LettuceConnectionFactory lettuce) {
            System.out.println("Lettuce host: " + lettuce.getHostName());
            System.out.println("Lettuce port: " + lettuce.getPort());
        }
    }

    @Test
    void printRedisConfig() {
        System.out.println("Container host: " + REDIS.getHost());

        System.out.println("Container port: " + REDIS.getMappedPort(6379));

        System.out.println("Spring Redis host: " + environment.getProperty("spring.data.redis.host"));

        System.out.println("Spring Redis port: " + environment.getProperty("spring.data.redis.port"));
    }

    @Test
    public void nearbyOrdering() throws Exception {
        String base = "http://localhost:" + port;
        rest.postForEntity(base + "/drivers/d1/location", new LocationDto(37.7749, -122.4194), Void.class);
        rest.postForEntity(base + "/drivers/d2/location", new LocationDto(37.7758, -122.4183), Void.class);
        ResponseEntity<DriverCandidateDto[]> resp = rest.getForEntity(
                base + "/drivers/nearby?lat=37.7753&lon=-122.4189&radiusMeters=2000&limit=10",
                DriverCandidateDto[].class);
        DriverCandidateDto[] arr = resp.getBody();
        // Expect the closer driver first (d1)
        assertNotNull(arr);
        assertEquals("d1", arr[0].getDriverId());
    }
}

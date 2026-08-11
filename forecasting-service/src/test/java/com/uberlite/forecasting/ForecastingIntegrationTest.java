package com.uberlite.forecasting;

import com.uberlite.common.events.Topics;
import com.uberlite.common.events.TripEvent;
import com.uberlite.common.events.TripEventPayloadKeys;
import com.uberlite.common.events.TripState;
import com.uberlite.common.geo.H3Util;
import com.uberlite.forecasting.repository.entity.DemandCountEntity;
import com.uberlite.forecasting.repository.DemandCountRepository;
import org.apache.kafka.common.serialization.StringSerializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end through the real machinery: a {@code trip-events} message on an embedded broker has to
 * come back out as an incremented row in Postgres.
 *
 * <p>Worth a broker and a container because the parts most likely to be wrong are exactly the ones
 * a mock would hide — that the consumer's deserializer agrees with the serializer Trip Service
 * publishes with, and that the {@code ON CONFLICT} upsert is valid SQL against a real Postgres.
 */
@Testcontainers(disabledWithoutDocker = true)
@EmbeddedKafka(partitions = 1, topics = Topics.TRIP_EVENTS)
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=forecasting-service-test",
        "forecasting.zone=UTC",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class ForecastingIntegrationTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final double PICKUP_LAT = 47.6205;
    private static final double PICKUP_LON = -122.3493;

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("forecastingdb")
            .withUsername("uberlite")
            .withPassword("changeme");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /**
     * Publishes the way Trip Service does — {@code JacksonJsonSerializer} in {@code noTypeInfo()}
     * mode — so this test fails if the shared consumer config ever stops matching the producer.
     */
    @TestConfiguration
    static class TestProducerConfig {

        @Bean
        ProducerFactory<String, TripEvent> testProducerFactory(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
            JacksonJsonSerializer<TripEvent> serializer = new JacksonJsonSerializer<>();
            serializer.noTypeInfo();
            return new DefaultKafkaProducerFactory<>(
                    Map.of("bootstrap.servers", bootstrapServers), new StringSerializer(), serializer);
        }

        @Bean
        KafkaTemplate<String, TripEvent> testKafkaTemplate(ProducerFactory<String, TripEvent> pf) {
            return new KafkaTemplate<>(pf);
        }
    }

    @Autowired
    private KafkaTemplate<String, TripEvent> producer;

    @Autowired
    private DemandCountRepository demandCounts;

    @Test
    void aRequestedEventIncrementsTheBucketForItsPickupCellAndHour() {
        Instant requestedAt = Instant.parse("2026-08-10T18:30:00Z");
        String expectedCell = H3Util.latLngToCell(PICKUP_LAT, PICKUP_LON);
        LocalDateTime local = LocalDateTime.ofInstant(requestedAt, UTC);

        producer.send(Topics.TRIP_EVENTS, requestedEvent(UUID.randomUUID().toString(), requestedAt));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<DemandCountEntity> window = demandCounts.findWindow(
                    expectedCell, (short) local.getHour(), local.toLocalDate(), local.toLocalDate());

            assertThat(window).singleElement().satisfies(bucket -> {
                assertThat(bucket.getCount()).isEqualTo(1L);
                assertThat(bucket.getId().getH3Cell()).isEqualTo(expectedCell);
                assertThat(bucket.getId().getHourOfDay()).isEqualTo(local.getHour());
                assertThat(bucket.getId().getDayBucket()).isEqualTo(local.toLocalDate());
            });
        });
    }

    @Test
    void redeliveryOfTheSameTripDoesNotDoubleCountDemand() {
        Instant requestedAt = Instant.parse("2026-08-11T07:15:00Z");
        String tripId = UUID.randomUUID().toString();
        String cell = H3Util.latLngToCell(PICKUP_LAT, PICKUP_LON);
        LocalDateTime local = LocalDateTime.ofInstant(requestedAt, UTC);

        // At-least-once delivery makes this a case the service must survive, not a hypothetical.
        producer.send(Topics.TRIP_EVENTS, requestedEvent(tripId, requestedAt));
        producer.send(Topics.TRIP_EVENTS, requestedEvent(tripId, requestedAt));

        // Holds for a stretch rather than passing on first look, so a late second delivery still
        // has the chance to be (incorrectly) applied before the test declares success.
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(bucketCount(cell, local)).isEqualTo(1L));
    }

    @Test
    void transitionsOtherThanRequestedAreNotDemand() {
        Instant occurredAt = Instant.parse("2026-08-12T09:00:00Z");
        LocalDateTime local = LocalDateTime.ofInstant(occurredAt, UTC);
        String cell = H3Util.latLngToCell(PICKUP_LAT, PICKUP_LON);

        // A completed trip was already counted at REQUESTED. Counting it again would double every
        // fulfilled ride and leave the unfulfilled ones — the ones surge cares most about —
        // relatively under-weighted.
        producer.send(Topics.TRIP_EVENTS, new TripEvent(
                UUID.randomUUID().toString(),
                TripState.RIDER_PICKED_UP,
                TripState.COMPLETED,
                occurredAt,
                Map.of(TripEventPayloadKeys.PICKUP, Map.of("lat", PICKUP_LAT, "lon", PICKUP_LON))));

        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(bucketCount(cell, local)).isZero());
    }

    private long bucketCount(String cell, LocalDateTime at) {
        return demandCounts
                .findWindow(cell, (short) at.getHour(), at.toLocalDate(), at.toLocalDate())
                .stream()
                .mapToLong(DemandCountEntity::getCount)
                .sum();
    }

    private static TripEvent requestedEvent(String tripId, Instant requestedAt) {
        return new TripEvent(
                tripId,
                null,
                TripState.REQUESTED,
                requestedAt,
                Map.of(
                        TripEventPayloadKeys.RIDER_ID, "rider-1",
                        TripEventPayloadKeys.PICKUP, Map.of("lat", PICKUP_LAT, "lon", PICKUP_LON),
                        TripEventPayloadKeys.DROPOFF, Map.of("lat", 47.6097, "lon", -122.3331)));
    }
}


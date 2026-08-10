package com.uberlite.matchinganalytics;

import com.uberlite.common.events.Topics;
import com.uberlite.common.events.TripEvent;
import com.uberlite.common.events.TripEventPayloadKeys;
import com.uberlite.common.events.TripState;
import com.uberlite.matchinganalytics.domain.MatchOutcome;
import com.uberlite.matchinganalytics.repository.MatchLogEntity;
import com.uberlite.matchinganalytics.repository.MatchLogRepository;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Consumes real {@code trip-events} off an embedded broker and checks the rows that come out.
 *
 * <p>The interesting assertion is the third one: a trip where two drivers declined before a third
 * accepted. Trip Service's own row cannot express that history — it overwrites {@code driver_id} on
 * each retry — so reconstructing it from the event stream is the entire justification for this
 * service existing.
 */
@Testcontainers(disabledWithoutDocker = true)
@EmbeddedKafka(partitions = 1, topics = Topics.TRIP_EVENTS)
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=matching-analytics-test",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class MatchingAnalyticsIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("matchinganalyticsdb")
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

    @TestConfiguration
    static class TestProducerConfig {

        @Bean
        ProducerFactory<String, TripEvent> testProducerFactory(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
            JacksonJsonSerializer<TripEvent> serializer = new JacksonJsonSerializer<>();
            serializer.noTypeInfo(); // exactly how Trip Service publishes
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
    private MatchLogRepository matchLog;

    @Test
    void aDriverProposedEventBecomesAMatchLogRow() {
        String tripId = UUID.randomUUID().toString();
        Instant occurredAt = Instant.parse("2026-08-10T18:30:00Z");

        producer.send(Topics.TRIP_EVENTS, matchEvent(
                tripId, TripState.ACCEPTED_BY_RIDER, TripState.DRIVER_PROPOSED, "driver-7", occurredAt));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(matchLog.findByTripIdOrderByOccurredAtAsc(tripId))
                        .singleElement()
                        .satisfies(row -> {
                            assertThat(row.getDriverId()).isEqualTo("driver-7");
                            assertThat(row.getOutcome()).isEqualTo(MatchOutcome.PROPOSED);
                            // Stamped from the event, not from the consumer's clock, so a replayed
                            // backlog keeps its original timeline.
                            assertThat(row.getOccurredAt()).isEqualTo(occurredAt);
                        }));
    }

    @Test
    void ignoresTransitionsThatSayNothingAboutMatching() {
        String tripId = UUID.randomUUID().toString();

        producer.send(Topics.TRIP_EVENTS, new TripEvent(
                tripId, TripState.REQUESTED, TripState.PRICED,
                Instant.parse("2026-08-10T18:00:00Z"),
                Map.of(TripEventPayloadKeys.QUOTED_PRICE, 12.50)));

        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(matchLog.findByTripIdOrderByOccurredAtAsc(tripId)).isEmpty());
    }

    @Test
    void preservesTheFullProposeDeclineAcceptHistoryOfOneTrip() {
        String tripId = UUID.randomUUID().toString();
        Instant t0 = Instant.parse("2026-08-10T19:00:00Z");

        producer.send(Topics.TRIP_EVENTS, matchEvent(
                tripId, TripState.ACCEPTED_BY_RIDER, TripState.DRIVER_PROPOSED, "driver-1", t0));
        producer.send(Topics.TRIP_EVENTS, matchEvent(
                tripId, TripState.DRIVER_PROPOSED, TripState.DRIVER_DECLINED, "driver-1", t0.plusSeconds(10)));
        producer.send(Topics.TRIP_EVENTS, matchEvent(
                tripId, TripState.DRIVER_DECLINED, TripState.DRIVER_PROPOSED, "driver-2", t0.plusSeconds(20)));
        producer.send(Topics.TRIP_EVENTS, matchEvent(
                tripId, TripState.DRIVER_PROPOSED, TripState.DRIVER_ACCEPTED, "driver-2", t0.plusSeconds(30)));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(matchLog.findByTripIdOrderByOccurredAtAsc(tripId))
                        .extracting(MatchLogEntity::getDriverId, MatchLogEntity::getOutcome)
                        .containsExactly(
                                org.assertj.core.groups.Tuple.tuple("driver-1", MatchOutcome.PROPOSED),
                                org.assertj.core.groups.Tuple.tuple("driver-1", MatchOutcome.DECLINED),
                                org.assertj.core.groups.Tuple.tuple("driver-2", MatchOutcome.PROPOSED),
                                org.assertj.core.groups.Tuple.tuple("driver-2", MatchOutcome.ACCEPTED)));
    }

    @Test
    void redeliveryDoesNotDuplicateARow() {
        String tripId = UUID.randomUUID().toString();
        Instant occurredAt = Instant.parse("2026-08-10T20:00:00Z");

        producer.send(Topics.TRIP_EVENTS, matchEvent(
                tripId, TripState.ACCEPTED_BY_RIDER, TripState.DRIVER_PROPOSED, "driver-9", occurredAt));
        producer.send(Topics.TRIP_EVENTS, matchEvent(
                tripId, TripState.ACCEPTED_BY_RIDER, TripState.DRIVER_PROPOSED, "driver-9", occurredAt));

        // A duplicate would quietly skew any decline rate computed from this table.
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(matchLog.findByTripIdOrderByOccurredAtAsc(tripId)).hasSize(1));
    }

    private static TripEvent matchEvent(String tripId, TripState from, TripState to,
                                        String driverId, Instant occurredAt) {
        return new TripEvent(tripId, from, to, occurredAt,
                Map.of(TripEventPayloadKeys.DRIVER_ID, driverId,
                        TripEventPayloadKeys.ETA_SECONDS, 240));
    }
}


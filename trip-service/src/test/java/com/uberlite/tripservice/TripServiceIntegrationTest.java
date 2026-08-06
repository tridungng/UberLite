package com.uberlite.tripservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uberlite.common.events.Topics;
import com.uberlite.common.events.TripEvent;
import com.uberlite.common.events.TripState;
import com.uberlite.tripservice.repository.TripRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.datasource.url=jdbc:h2:mem:tripdb;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.jpa.properties.hibernate.default_schema=trip",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = Topics.TRIP_EVENTS)
class TripServiceIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private TripRepository tripRepository;

    @Test
    void transitionWritesDatabaseRowAndKafkaEvent() throws Exception {
        Consumer<String, String> consumer = createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, Topics.TRIP_EVENTS);

        String createResponse = mockMvc.perform(post("/trips")
                        .contentType("application/json")
                        .content("""
                                {"riderId":"rider-1","pickup":{"lat":37.0,"lon":-122.0},"dropoff":{"lat":37.5,"lon":-122.3}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("REQUESTED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String tripId = new ObjectMapper().readTree(createResponse).get("id").asText();

        mockMvc.perform(post("/trips/" + tripId + "/transition")
                        .contentType("application/json")
                        .content("""
                                {"toState":"PRICED","payload":{"quoteId":"quote-1"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PRICED"));

        assertThat(tripRepository.findById(java.util.UUID.fromString(tripId))).isPresent();
        assertThat(tripRepository.findById(java.util.UUID.fromString(tripId)).orElseThrow().getState()).isEqualTo(TripState.PRICED);

        String eventJson = KafkaTestUtils.getSingleRecord(consumer, Topics.TRIP_EVENTS).value();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        TripEvent event = mapper.readValue(eventJson, TripEvent.class);

        assertThat(event.getTripId()).isEqualTo(tripId);
        assertThat(event.getFromState()).isEqualTo(TripState.REQUESTED);
        assertThat(event.getToState()).isEqualTo(TripState.PRICED);
        assertThat(event.getPayload()).isEqualTo(Map.of("quoteId", "quote-1"));
    }

    private Consumer<String, String> createConsumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("trip-service-it", "false", embeddedKafka);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new org.springframework.kafka.core.DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new StringDeserializer()
        ).createConsumer();
    }
}

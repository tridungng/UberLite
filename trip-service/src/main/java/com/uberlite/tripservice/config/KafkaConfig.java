package com.uberlite.tripservice.config;

import com.uberlite.common.events.Topics;
import com.uberlite.common.events.TripEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {
    @Bean
    public ProducerFactory<String, TripEvent> tripEventProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties());
        JsonSerializer<TripEvent> jsonSerializer = new JsonSerializer<>();
        jsonSerializer.setAddTypeInfo(false);
        return new DefaultKafkaProducerFactory<>(props, new StringSerializer(), jsonSerializer);
    }

    @Bean
    public KafkaTemplate<String, TripEvent> tripEventKafkaTemplate(ProducerFactory<String, TripEvent> tripEventProducerFactory) {
        return new KafkaTemplate<>(tripEventProducerFactory);
    }

    @Bean
    public NewTopic tripEventsTopic() {
        return TopicBuilder.name(Topics.TRIP_EVENTS).partitions(1).replicas(1).build();
    }
}

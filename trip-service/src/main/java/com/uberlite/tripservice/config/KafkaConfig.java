package com.uberlite.tripservice.config;

import com.uberlite.common.events.Topics;
import com.uberlite.common.events.TripEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Autowired
    private Environment env;

    @Bean
    public ProducerFactory<String, TripEvent> tripEventProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        String bootstrap = env.getProperty("spring.kafka.bootstrap-servers", "");
        if (!bootstrap.isBlank()) {
            props.put("bootstrap.servers", bootstrap);
        }
        // sensible defaults for tests and local runs
        props.putIfAbsent("key.serializer", org.apache.kafka.common.serialization.StringSerializer.class);
        props.putIfAbsent("value.serializer", org.springframework.kafka.support.serializer.JacksonJsonSerializer.class);

        JacksonJsonSerializer<TripEvent> jsonSerializer = new JacksonJsonSerializer<>();
        jsonSerializer.noTypeInfo();
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

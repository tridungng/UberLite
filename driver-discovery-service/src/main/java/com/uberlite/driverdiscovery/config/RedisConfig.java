package com.uberlite.driverdiscovery.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for the Driver Discovery service.
 * <p>
 * Exposes a RedisTemplate configured to use string serializers for keys and values.
 */
@Configuration
public class RedisConfig {

    /**
     * Create a RedisTemplate<String,String> with string serializers wired to the
     * given connection factory. Using string serializers simplifies storage of
     * small DTO fields and makes the data human-readable in Redis tools.
     *
     * @param connectionFactory Redis connection factory provided by Spring
     * @return configured RedisTemplate for string keys and values
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> rt = new RedisTemplate<>();
        rt.setConnectionFactory(connectionFactory);
        rt.setKeySerializer(new StringRedisSerializer());
        rt.setValueSerializer(new StringRedisSerializer());
        rt.setHashKeySerializer(new StringRedisSerializer());
        rt.setHashValueSerializer(new StringRedisSerializer());
        return rt;
    }
}

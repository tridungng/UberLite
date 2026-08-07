package com.uberlite.driverdiscovery;

import com.uberlite.driverdiscovery.service.DriverStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class DriverStoreServiceEvictionTest {
    RedisTemplate<String,String> redis;
    ZSetOperations<String,String> zOps;
    GeoOperations<String,String> geoOps;

    @BeforeEach
    public void setup() {
        redis = Mockito.mock(RedisTemplate.class);
        zOps = Mockito.mock(ZSetOperations.class);
        geoOps = Mockito.mock(GeoOperations.class);
        when(redis.opsForZSet()).thenReturn(zOps);
        when(redis.opsForGeo()).thenReturn(geoOps);
    }

    @Test
    public void evictStale() {
        long now = Instant.parse("2026-08-07T12:00:00Z").getEpochSecond();
        Clock clock = Clock.fixed(Instant.parse("2026-08-07T12:00:00Z"), ZoneOffset.UTC);
        when(zOps.range("drivers:active", 0, -1)).thenReturn(Set.of("d1","d2"));
        when(redis.opsForHash()).thenReturn(Mockito.mock(org.springframework.data.redis.core.HashOperations.class));
        org.springframework.data.redis.core.HashOperations hs = redis.opsForHash();
        when(hs.get("driver:d1","lastSeen")).thenReturn(String.valueOf(now - 60));
        when(hs.get("driver:d2","lastSeen")).thenReturn(String.valueOf(now - 200));

        DriverStoreService svc = new DriverStoreService(redis, clock);
        var removed = svc.evictStaleAndReturnRemoved(120);
        assertEquals(1, removed.size());
        assertEquals("d2", removed.get(0));
        verify(zOps).remove("drivers:active", "d2");
    }
}

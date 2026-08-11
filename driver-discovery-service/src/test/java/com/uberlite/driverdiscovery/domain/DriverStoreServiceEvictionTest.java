package com.uberlite.driverdiscovery.domain;

import com.uberlite.driverdiscovery.domain.DriverStoreService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverStoreServiceEvictionTest {
    @Mock
    private RedisTemplate<String, String> redis;

    @Mock
    private ZSetOperations<String, String> zOps;

    @Mock
    private GeoOperations<String, String> geoOps;

    @Mock
    private HashOperations<String, Object, Object> hs;

    @BeforeEach
    void setup() {
        when(redis.opsForZSet()).thenReturn(zOps);
        when(redis.opsForGeo()).thenReturn(geoOps);
    }

    @Test
    void evictStale() {
        long now = Instant.parse("2026-08-07T12:00:00Z").getEpochSecond();
        Clock clock = Clock.fixed(Instant.parse("2026-08-07T12:00:00Z"), ZoneOffset.UTC);
        when(zOps.range("drivers:active", 0, -1)).thenReturn(Set.of("d1", "d2"));
        when(redis.opsForHash()).thenReturn(hs);
        when(hs.get("driver:d1", "lastSeen")).thenReturn(String.valueOf(now - 60));
        when(hs.get("driver:d2", "lastSeen")).thenReturn(String.valueOf(now - 200));

        DriverStoreService svc = new DriverStoreService(redis, clock);
        var removed = svc.evictStaleAndReturnRemoved(120);
        assertEquals(1, removed.size());
        assertEquals("d2", removed.getFirst());
        verify(zOps).remove("drivers:active", "d2");
    }
}

package com.uberlite.driverdiscovery.service;

import com.uberlite.common.dto.DriverCandidateDto;
import com.uberlite.common.dto.LocationDto;
import com.uberlite.common.geo.H3Util;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DriverStoreService {
    private static final String GEO_KEY = "drivers:active";
    private final RedisTemplate<String, String> redis;
    private final GeoOperations<String, String> geoOps;
    private final ZSetOperations<String, String> zOps;
    private final Clock clock;

    public DriverStoreService(RedisTemplate<String, String> redis, Clock clock) {
        this.redis = redis;
        this.geoOps = redis.opsForGeo();
        this.zOps = redis.opsForZSet();
        this.clock = clock;
    }

    public void updateLocation(String driverId, LocationDto loc) {
        geoOps.add(GEO_KEY, new Point(loc.getLon(), loc.getLat()), driverId);
        String cell = H3Util.latLngToCell(loc.getLat(), loc.getLon());
        String hash = "driver:" + driverId;
        Map<String, String> m = new HashMap<>();
        m.put("h3Cell", cell);
        m.put("lastSeen", String.valueOf(Instant.now(clock).getEpochSecond()));
        Object status = redis.opsForHash().get(hash, "status");
        if (status == null) m.put("status", "ONLINE");
        redis.opsForHash().putAll(hash, m);
    }

    public void setStatus(String driverId, String status) {
        String hash = "driver:" + driverId;
        redis.opsForHash().put(hash, "status", status);
        if ("OFFLINE".equals(status)) {
            zOps.remove(GEO_KEY, driverId);
        }
    }

    public List<DriverCandidateDto> nearby(double lat, double lon, double radiusMeters, int limit) {
        Set<String> ids = Optional.ofNullable(zOps.range(GEO_KEY, 0, -1)).orElse(Collections.emptySet());
        List<DriverCandidateDto> list = new ArrayList<>();
        List<Map.Entry<String, Double>> withDist = new ArrayList<>();
        for (String id : ids) {
            List<Point> pos = geoOps.position(GEO_KEY, id);
            if (pos == null || pos.isEmpty()) continue;
            double dlat = pos.get(0).getY();
            double dlon = pos.get(0).getX();
            double dist = haversineMeters(lat, lon, dlat, dlon);
            if (dist <= radiusMeters) withDist.add(Map.entry(id, dist));
        }
        withDist.sort(Comparator.comparingDouble(Map.Entry::getValue));
        for (int i = 0; i < Math.min(limit, withDist.size()); i++) {
            String id = withDist.get(i).getKey();
            List<Point> pos = geoOps.position(GEO_KEY, id);
            double dlat = pos.get(0).getY();
            double dlon = pos.get(0).getX();
            list.add(new DriverCandidateDto(id, new LocationDto(dlat, dlon), 0));
        }
        return list;
    }

    public List<DriverCandidateDto> nearbyByCell(String h3Cell, int kRing, int limit) {
        List<String> cells = H3Util.gridDisk(h3Cell, kRing);
        Set<String> all = Optional.ofNullable(zOps.range(GEO_KEY, 0, -1)).orElse(Collections.emptySet());
        List<DriverCandidateDto> res = new ArrayList<>();
        for (String id : all) {
            String h = (String) redis.opsForHash().get("driver:" + id, "h3Cell");
            if (h != null && cells.contains(h)) {
                List<Point> pos = geoOps.position(GEO_KEY, id);
                if (pos != null && !pos.isEmpty()) {
                    double lon = pos.get(0).getX(), lat = pos.get(0).getY();
                    res.add(new DriverCandidateDto(id, new LocationDto(lat, lon), 0));
                    if (res.size() >= limit) break;
                }
            }
        }
        return res;
    }

    public List<String> evictStaleAndReturnRemoved(long olderThanEpochSeconds) {
        Set<String> ids = Optional.ofNullable(zOps.range(GEO_KEY, 0, -1)).orElse(Collections.emptySet());
        List<String> removed = new ArrayList<>();
        long now = Instant.now(clock).getEpochSecond();
        for (String id : ids) {
            String last = (String) redis.opsForHash().get("driver:" + id, "lastSeen");
            long lastSeen = last == null ? 0L : Long.parseLong(last);
            if (now - lastSeen > olderThanEpochSeconds) {
                zOps.remove(GEO_KEY, id);
                redis.opsForHash().put("driver:" + id, "status", "OFFLINE");
                removed.add(id);
            }
        }
        return removed;
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }
}

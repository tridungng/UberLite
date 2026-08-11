package com.uberlite.timeestimation.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uberlite.timeestimation.config.TimeEstimationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Time Estimation Service (ARCHITECTURE.md Sec. 2, "TES").
 *
 * <p>MVP simplification: instead of live traffic the estimate is a base duration scaled by a static
 * per-cell "heat" multiplier loaded once at startup from a seed file. A real traffic feed plugs in
 * by replacing {@link #multiplierFor(double, double)} — the HTTP contract does not change.
 */
@Service
public class HeatmapService {

    private static final Logger log = LoggerFactory.getLogger(HeatmapService.class);

    /** Cells absent from the seed file are free-flowing, i.e. no slowdown. */
    private static final double DEFAULT_MULTIPLIER = 1.0;

    private final Map<String, Double> heatmap;
    private final double baseMinutes;

    public HeatmapService(TimeEstimationProperties properties, ObjectMapper objectMapper) {
        this.baseMinutes = properties.getBaseMinutes();
        this.heatmap = loadSeed(properties.getHeatmapSeed(), objectMapper);
        log.info("Loaded {} heat map cells from {}", heatmap.size(), properties.getHeatmapSeed());
    }

    /**
     * A missing or unreadable seed file is not fatal: the service degrades to a flat
     * {@link #DEFAULT_MULTIPLIER} everywhere rather than refusing to start. A time estimate that is
     * optimistic is a much smaller problem than a price-estimation fan-out that cannot complete.
     */
    private static Map<String, Double> loadSeed(Resource seed, ObjectMapper objectMapper) {
        if (seed == null || !seed.exists()) {
            log.warn("Heat map seed {} not found; every cell will use multiplier {}", seed, DEFAULT_MULTIPLIER);
            return Map.of();
        }
        try (InputStream is = seed.getInputStream()) {
            return Map.copyOf(objectMapper.readValue(is, new TypeReference<Map<String, Double>>() {}));
        } catch (IOException e) {
            log.warn("Heat map seed {} could not be read; falling back to multiplier {}", seed, DEFAULT_MULTIPLIER, e);
            return Map.of();
        }
    }

    /**
     * Key a point to a seed-file cell. Whole-degree truncation, not H3: the seed table is a demo
     * fixture and H3 resolution would imply a precision the data does not have.
     */
    private static String cellKey(double lat, double lon) {
        return (int) Math.floor(lat) + "," + (int) Math.floor(lon);
    }

    /** Traffic multiplier for the cell containing the point; {@value #DEFAULT_MULTIPLIER} if unseeded. */
    public double multiplierFor(double lat, double lon) {
        return heatmap.getOrDefault(cellKey(lat, lon), DEFAULT_MULTIPLIER);
    }

    /** Estimated travel time in minutes for a trip starting at the given point. */
    public double estimateMinutes(double lat, double lon) {
        return baseMinutes * multiplierFor(lat, lon);
    }
}

package com.uberlite.timeestimation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

public class HeatmapService {
    private final Map<String, Double> heatmap;
    private static final double BASE_MINUTES = 10.0;

    public HeatmapService() {
        Map<String, Double> loaded;
        try (InputStream is = getClass().getResourceAsStream("/heatmap-seed.json")) {
            if (is == null) {
                loaded = Collections.emptyMap();
            } else {
                ObjectMapper om = new ObjectMapper();
                loaded = om.readValue(is, new TypeReference<Map<String, Double>>(){});
            }
        } catch (Exception e) {
            loaded = Collections.emptyMap();
        }
        this.heatmap = loaded;
    }

    private String cellKey(double lat, double lon) {
        int rlat = (int) Math.floor(lat);
        int rlon = (int) Math.floor(lon);
        return rlat + "," + rlon;
    }

    public double estimateMinutes(double lat, double lon) {
        String key = cellKey(lat, lon);
        double mult = heatmap.getOrDefault(key, 1.0);
        return BASE_MINUTES * mult;
    }
}

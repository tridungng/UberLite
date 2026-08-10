package com.uberlite.forecasting.api;

import com.uberlite.common.dto.DemandForecastDto;
import com.uberlite.forecasting.domain.DemandForecaster;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Forecasting Service read API (ARCHITECTURE.md Sec. 2, background services).
 *
 * <p>The intended caller is Surge Pricing v2 — see {@link DemandForecaster} for why that is not
 * wired up yet.
 */
@RestController
public class ForecastController {

    private final DemandForecaster forecaster;

    public ForecastController(DemandForecaster forecaster) {
        this.forecaster = forecaster;
    }

    /**
     * @return {@code 200} with the rolling-average forecast; a cell with no recorded history
     *     forecasts {@code 0}, which is a real answer rather than a {@code 404} — "nobody requests
     *     rides here" is precisely what a caller pricing that cell needs to know
     */
    @GetMapping("/forecast/{h3Cell}")
    public DemandForecastDto forecast(@PathVariable String h3Cell,
                                      @RequestParam("hourOfDay") int hourOfDay) {
        return forecaster.forecast(h3Cell, hourOfDay);
    }
}


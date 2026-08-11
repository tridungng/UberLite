package com.uberlite.forecasting.domain;

import com.uberlite.common.dto.DemandForecastDto;
import com.uberlite.forecasting.config.ForecastingProperties;
import com.uberlite.forecasting.repository.entity.DemandCountEntity;
import com.uberlite.forecasting.repository.DemandCountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * The MVP forecast: a rolling average of recorded demand per H3 cell and hour of day
 * (ARCHITECTURE.md Sec. 2, background services — "no weather/events input").
 *
 * <h2>Where a model plugs in (paper Sec. 5)</h2>
 *
 * <p>This class is the seam. {@link #forecast(String, int)} is the whole public surface, and it
 * returns a {@link DemandForecastDto} rather than exposing buckets, so a learned model can replace
 * the body without any caller noticing. The features such a model would want — the same
 * {@code demand_counts} table, plus the weather and event calendars the MVP does not ingest — are
 * already keyed the way it would need them.
 *
 * <h2>Where the forecast is meant to be consumed (not wired in this issue)</h2>
 *
 * <p>Surge Pricing today computes {@code pending_requests / active_drivers} from live counters only
 * (ARCHITECTURE.md Sec. 2), which makes it purely reactive: surge appears only after riders are
 * already waiting. The planned "surge-pricing-service v2" is to call {@code GET /forecast/{h3Cell}}
 * and use the predicted demand to pre-position the clamp bounds, so a cell that is reliably busy at
 * 18:00 starts pricing up before the queue forms. Nothing calls this endpoint yet — that wiring is
 * a separate issue, and doing it here would change Surge Pricing's published contract without a
 * spec for it.
 */
@Service
public class DemandForecaster {

    private final DemandCountRepository demandCounts;
    private final ForecastingProperties properties;
    private final Clock clock;

    public DemandForecaster(DemandCountRepository demandCounts,
                            ForecastingProperties properties,
                            Clock clock) {
        this.demandCounts = demandCounts;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @param h3Cell the cell to forecast; not validated against the H3 grid because an unknown cell
     *     is indistinguishable from a real cell with no history, and both honestly forecast zero
     * @param hourOfDay 0–23, interpreted in {@link ForecastingProperties#zone()}
     * @throws IllegalArgumentException if {@code hourOfDay} is outside 0–23
     */
    @Transactional(readOnly = true)
    public DemandForecastDto forecast(String h3Cell, int hourOfDay) {
        if (hourOfDay < 0 || hourOfDay > 23) {
            throw new IllegalArgumentException("hourOfDay must be between 0 and 23, was " + hourOfDay);
        }

        ForecastWindow window = ForecastWindow.endingAtLastCompleteBucket(
                clock.instant(), properties.zone(), hourOfDay, properties.windowDays());

        List<Long> counts = demandCounts
                .findWindow(h3Cell, (short) hourOfDay, window.from(), window.to())
                .stream()
                .map(DemandCountEntity::getCount)
                .toList();

        return new DemandForecastDto(h3Cell, hourOfDay, window.average(counts));
    }
}

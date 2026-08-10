package com.uberlite.forecasting.api;

import com.uberlite.common.dto.DemandForecastDto;
import com.uberlite.forecasting.domain.DemandForecaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pins the wire shape of {@code GET /forecast/{h3Cell}} — the contract Surge Pricing v2 will call. */
@ExtendWith(MockitoExtension.class)
class ForecastControllerTest {

    private static final String CELL = "8828308281fffff";

    @Mock
    private DemandForecaster forecaster;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ForecastController(forecaster))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void returnsCellHourAndPredictedDemand() throws Exception {
        when(forecaster.forecast(CELL, 18)).thenReturn(new DemandForecastDto(CELL, 18, 4.5));

        mockMvc.perform(get("/forecast/{h3Cell}", CELL).param("hourOfDay", "18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.h3Cell").value(CELL))
                .andExpect(jsonPath("$.hourOfDay").value(18))
                .andExpect(jsonPath("$.predictedDemand").value(4.5));
    }

    @Test
    void rejectsAnHourOutsideTheDayWithFourHundred() throws Exception {
        when(forecaster.forecast(anyString(), anyInt()))
                .thenThrow(new IllegalArgumentException("hourOfDay must be between 0 and 23, was 24"));

        // A 500 here would tell the caller to retry a request that can never succeed.
        mockMvc.perform(get("/forecast/{h3Cell}", CELL).param("hourOfDay", "24"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresTheHourOfDayParameter() throws Exception {
        // Defaulting to "now" would make the same URL mean different things at different times.
        mockMvc.perform(get("/forecast/{h3Cell}", CELL))
                .andExpect(status().isBadRequest());
    }
}

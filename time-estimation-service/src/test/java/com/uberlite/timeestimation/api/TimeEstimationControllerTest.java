package com.uberlite.timeestimation.api;

import com.uberlite.timeestimation.domain.HeatmapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the wire shape of {@code GET /time/estimate}.
 *
 * <p>price-estimation-service binds this response to {@code TimeEstimateDto}, whose only wire field
 * is {@code minutes}. Renaming it would compile on both sides and fail only at runtime.
 */
@ExtendWith(MockitoExtension.class)
class TimeEstimationControllerTest {

    @Mock
    private HeatmapService heatmapService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TimeEstimationController(heatmapService)).build();
    }

    @Test
    void returnsTheEstimateUnderTheMinutesField() throws Exception {
        when(heatmapService.estimateMinutes(1.2, 1.3)).thenReturn(30.0);

        mockMvc.perform(get("/time/estimate").param("lat", "1.2").param("lon", "1.3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minutes").value(30.0));
    }

    @Test
    void rejectsAMissingCoordinateWith400() throws Exception {
        mockMvc.perform(get("/time/estimate").param("lat", "1.2"))
                .andExpect(status().isBadRequest());
    }
}

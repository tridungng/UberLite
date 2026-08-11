package com.uberlite.routeservice.api;

import com.uberlite.routeservice.domain.RouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the wire shape of {@code GET /route/estimate}.
 *
 * <p>Both price-estimation-service and matching-service bind this response to
 * {@code RouteEstimateDto}. The nullability of {@code detourFactor} is part of the contract: a 0
 * would collapse the trip distance and produce a free ride, so the absent case is asserted rather
 * than assumed.
 *
 * <p>The real {@link RouteService} is used instead of a mock — it is a pure function with no
 * collaborators, so stubbing it would only assert that Jackson can serialize a constant.
 */
class RouteControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RouteController(new RouteService())).build();
    }

    @Test
    void returnsStraightLineDistanceAndANullDetourWhenNoActualDistanceGiven() throws Exception {
        mockMvc.perform(get("/route/estimate")
                        .param("lat1", "0.0").param("lon1", "0.0")
                        .param("lat2", "0.0").param("lon2", "1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.straightDistanceKm").value(closeTo(111.32, 1.0)))
                .andExpect(jsonPath("$.detourFactor").value(nullValue()));
    }

    @Test
    void computesDetourFactorWhenTheActualDistanceIsSupplied() throws Exception {
        mockMvc.perform(get("/route/estimate")
                        .param("lat1", "0.0").param("lon1", "0.0")
                        .param("lat2", "0.0").param("lon2", "1.0")
                        .param("actualDistanceKm", "222.64"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detourFactor").value(closeTo(2.0, 0.05)));
    }

    @Test
    void rejectsAMissingCoordinateWith400() throws Exception {
        mockMvc.perform(get("/route/estimate").param("lat1", "0.0").param("lon1", "0.0"))
                .andExpect(status().isBadRequest());
    }
}

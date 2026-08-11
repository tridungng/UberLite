package com.uberlite.surgepricing.api;

import com.uberlite.surgepricing.domain.SurgeComputationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the wire shape of the Surge Pricing API.
 *
 * <p>{@code GET /surge/{h3Cell}} is bound to {@code SurgeMultiplierDto} by price-estimation-service,
 * and the pending-request counter is driven by trip-service (ARCHITECTURE.md Sec. 4). The HTTP verb
 * carries the sign — {@code POST} increments, {@code DELETE} decrements — so both are asserted to
 * reach the matching domain call rather than the other one.
 */
@ExtendWith(MockitoExtension.class)
class SurgeControllerTest {

    private static final String CELL = "8828308281fffff";

    @Mock
    private SurgeComputationService surgeComputationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SurgeController(surgeComputationService)).build();
    }

    @Test
    void returnsCellMultiplierAndTimestamp() throws Exception {
        when(surgeComputationService.getMultiplier(CELL)).thenReturn(1.75);

        mockMvc.perform(get("/surge/{cell}", CELL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.h3Cell").value(CELL))
                .andExpect(jsonPath("$.multiplier").value(1.75))
                .andExpect(jsonPath("$.updatedAtMs").isNumber());
    }

    @Test
    void postIncrementsThePendingRequestCounter() throws Exception {
        mockMvc.perform(post("/surge/{cell}/pending-request", CELL))
                .andExpect(status().isOk());

        verify(surgeComputationService).incrementPendingRequest(CELL);
    }

    @Test
    void deleteDecrementsThePendingRequestCounter() throws Exception {
        mockMvc.perform(delete("/surge/{cell}/pending-request", CELL))
                .andExpect(status().isOk());

        verify(surgeComputationService).decrementPendingRequest(CELL);
    }
}


package com.uberlite.taxtolls.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uberlite.common.dto.RouteDto;
import com.uberlite.taxtolls.domain.TaxTollInfo;
import com.uberlite.taxtolls.domain.TaxTollLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TaxTollsControllerTest {
    @Mock
    private TaxTollLookup taxTollLookup;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TaxTollsController(taxTollLookup)).build();
    }

    @Test
    void getTaxReturnsSeededRateShape() throws Exception {
        when(taxTollLookup.lookupByRegion("CA")).thenReturn(new TaxTollInfo("CA", 0.0725, 2.50));

        mockMvc.perform(get("/tax/CA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regionId").value("CA"))
                .andExpect(jsonPath("$.rate").value(0.0725));
    }

    @Test
    void estimateTollReturnsAmount() throws Exception {
        when(taxTollLookup.estimateToll(any(RouteDto.class))).thenReturn(2.50);
        String payload = new ObjectMapper().writeValueAsString(new RouteDto(21_000L, List.of()));

        mockMvc.perform(post("/tolls/estimate")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(2.50));
    }
}

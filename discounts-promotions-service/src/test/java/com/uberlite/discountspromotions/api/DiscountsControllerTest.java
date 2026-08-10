package com.uberlite.discountspromotions.api;

import com.uberlite.discountspromotions.domain.DiscountEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DiscountsControllerTest {
    @Mock
    private DiscountEvaluator discountEvaluator;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DiscountsController(discountEvaluator)).build();
    }

    @Test
    void evaluateReturnsDiscountPct() throws Exception {
        when(discountEvaluator.evaluate("rider-1", 2)).thenReturn(0.20);

        mockMvc.perform(post("/discounts/evaluate")
                        .contentType("application/json")
                        .content("{\"riderId\":\"rider-1\",\"riderTripCount\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountPct").value(0.20));
    }
}

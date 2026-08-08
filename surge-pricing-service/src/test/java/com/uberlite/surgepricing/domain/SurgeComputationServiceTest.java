package com.uberlite.surgepricing.domain;

import com.uberlite.common.dto.DriverCandidateDto;
import com.uberlite.common.dto.LocationDto;
import com.uberlite.surgepricing.client.DriverDiscoveryClient;
import com.uberlite.surgepricing.repository.SurgeRepository;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SurgeComputationService with mocked dependencies.
 */
@DisplayName("SurgeComputationService tests")
class SurgeComputationServiceTest {

    private SurgeRepository mockRepository;
    private DriverDiscoveryClient mockDriverDiscovery;
    private SurgeMultiplierService multiplierService;
    private SurgeComputationService service;

    @BeforeEach
    void setUp() {
        mockRepository = mock(SurgeRepository.class);
        mockDriverDiscovery = mock(DriverDiscoveryClient.class);
        multiplierService = new SurgeMultiplierService();
        service = new SurgeComputationService(mockRepository, mockDriverDiscovery, multiplierService);
    }

    @Test
    @DisplayName("getMultiplier returns cached value if available")
    void testGetMultiplierReturnsCache() {
        String h3Cell = "test-cell";
        when(mockRepository.getCachedMultiplier(h3Cell)).thenReturn(1.5);

        double result = service.getMultiplier(h3Cell);

        assertEquals(1.5, result, 0.001);
        // Should not call driver discovery when cache hit
        verify(mockDriverDiscovery, never()).getDriversInCell(anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("getMultiplier computes and caches when not in cache")
    void testGetMultiplierComputes() {
        String h3Cell = "test-cell";
        when(mockRepository.getCachedMultiplier(h3Cell)).thenReturn(null);
        when(mockRepository.getPendingRequests(h3Cell)).thenReturn(15L);

        List<DriverCandidateDto> drivers = createDriverList(10);
        when(mockDriverDiscovery.getDriversInCell(eq(h3Cell), anyInt(), anyInt()))
                .thenReturn(drivers);

        double result = service.getMultiplier(h3Cell);

        // 15 pending / 10 drivers = 1.5
        assertEquals(1.5, result, 0.001);

        // Verify it cached the result
        verify(mockRepository, times(1)).cacheMultiplier(eq(h3Cell), eq(1.5), anyLong());
    }

    @Test
    @DisplayName("getMultiplier handles driver discovery failure gracefully")
    void testGetMultiplierFallbackOnDriverDiscoveryError() {
        String h3Cell = "test-cell";
        when(mockRepository.getCachedMultiplier(h3Cell)).thenReturn(null);
        when(mockRepository.getPendingRequests(h3Cell)).thenReturn(10L);
        when(mockDriverDiscovery.getDriversInCell(eq(h3Cell), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("Service unavailable"));

        double result = service.getMultiplier(h3Cell);

        // 10 pending / 1 (fallback) = 10.0, clamped to 3.0
        assertEquals(3.0, result, 0.001);
    }

    @Test
    @DisplayName("getMultiplier with empty driver list (fallback)")
    void testGetMultiplierEmptyDriverList() {
        String h3Cell = "test-cell";
        when(mockRepository.getCachedMultiplier(h3Cell)).thenReturn(null);
        when(mockRepository.getPendingRequests(h3Cell)).thenReturn(10L);
        when(mockDriverDiscovery.getDriversInCell(eq(h3Cell), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        double result = service.getMultiplier(h3Cell);

        // 10 pending / 1 (fallback) = 10.0, clamped to 3.0
        assertEquals(3.0, result, 0.001);
    }

    @Test
    @DisplayName("incrementPendingRequest calls repository")
    void testIncrementPendingRequest() {
        String h3Cell = "test-cell";

        service.incrementPendingRequest(h3Cell);

        verify(mockRepository, times(1)).incrementPendingRequests(eq(h3Cell));
    }

    @Test
    @DisplayName("decrementPendingRequest calls repository")
    void testDecrementPendingRequest() {
        String h3Cell = "test-cell";

        service.decrementPendingRequest(h3Cell);

        verify(mockRepository, times(1)).decrementPendingRequests(eq(h3Cell));
    }

    @Test
    @DisplayName("Surge multiplier scales with demand")
    void testMultiplierScaling() {
        String h3Cell = "test-cell";
        when(mockRepository.getCachedMultiplier(h3Cell)).thenReturn(null);

        // Test different scenarios
        testScenario(h3Cell, 0, 10, 1.0);   // No demand
        testScenario(h3Cell, 5, 10, 1.0);   // 0.5, clamped to 1.0
        testScenario(h3Cell, 15, 10, 1.5);  // 1.5
        testScenario(h3Cell, 30, 10, 3.0);  // 3.0, clamped to max
    }

    private void testScenario(String h3Cell, long pending, int drivers, double expectedMultiplier) {
        when(mockRepository.getPendingRequests(h3Cell)).thenReturn(pending);
        when(mockDriverDiscovery.getDriversInCell(eq(h3Cell), anyInt(), anyInt()))
                .thenReturn(createDriverList(drivers));

        double result = service.getMultiplier(h3Cell);
        assertEquals(expectedMultiplier, result, 0.001);
    }

    private List<DriverCandidateDto> createDriverList(int count) {
        List<DriverCandidateDto> drivers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            drivers.add(new DriverCandidateDto(
                    "driver-" + i,
                    new LocationDto(37.7749, -122.4194),
                    60));
        }
        return drivers;
    }
}

package com.uberlite.tripservice.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Tunables for the cross-service orchestration in
 * {@link com.uberlite.tripservice.domain.TripOrchestrator}. These are operational levers (how hard
 * to push a sick or empty marketplace) and must be changeable without a rebuild.
 */
@ConfigurationProperties(prefix = "trip.orchestration")
@Validated
public class OrchestrationProperties {

    /**
     * How many times we re-ask Matching within a single attempt when it keeps proposing a driver
     * who already declined. Matching is stateless and has no exclusion list, so this bounds the
     * "keeps returning the same driver" case (ARCHITECTURE.md Sec. 4).
     */
    @Min(1)
    private int maxProposalsPerAttempt = 3;

    public int getMaxProposalsPerAttempt() {
        return maxProposalsPerAttempt;
    }

    public void setMaxProposalsPerAttempt(int maxProposalsPerAttempt) {
        this.maxProposalsPerAttempt = maxProposalsPerAttempt;
    }
}


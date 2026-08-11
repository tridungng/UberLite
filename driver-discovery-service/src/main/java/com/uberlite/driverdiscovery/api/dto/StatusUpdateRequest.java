package com.uberlite.driverdiscovery.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body of {@code POST /drivers/{driverId}/status}.
 *
 * <p>Lives in the module rather than in {@code common} because no other service posts a driver
 * status — only the driver app does, through the gateway. Promoting it to {@code common} would
 * publish a contract nothing consumes.
 */
public class StatusUpdateRequest {

    /** {@code ONLINE}, {@code BUSY} or {@code OFFLINE} — see ARCHITECTURE.md Sec. 7. */
    @NotBlank
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}


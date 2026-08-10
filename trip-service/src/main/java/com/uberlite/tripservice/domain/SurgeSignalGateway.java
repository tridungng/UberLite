package com.uberlite.tripservice.domain;

import com.uberlite.tripservice.client.SurgePricingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Feeds the demand side of the surge signal: how many riders are currently waiting in an H3 cell.
 *
 * <p><b>Best effort by design.</b> The pending-request counter is a pricing input, not trip state.
 * If SPS is down, the correct behaviour is a slightly stale surge multiplier — not a rider who
 * cannot book a car. So every failure here is logged and swallowed, and the caller is never given
 * the chance to fail a trip over it.
 */
@Component
public class SurgeSignalGateway {

    static final String DEPENDENCY = "surge-pricing-service";

    private static final Logger log = LoggerFactory.getLogger(SurgeSignalGateway.class);

    private final SurgePricingClient client;

    public SurgeSignalGateway(SurgePricingClient client) {
        this.client = client;
    }

    public void incrementPending(String h3Cell) {
        try {
            RemoteCalls.callVoid(DEPENDENCY, "incrementing pending requests in " + h3Cell,
                    () -> client.incrementPendingRequest(h3Cell));
        } catch (RuntimeException e) {
            log.warn("Surge pending-request increment for cell {} failed; the multiplier will "
                    + "under-report demand until the next trip in this cell", h3Cell, e);
        }
    }

    public void decrementPending(String h3Cell) {
        try {
            RemoteCalls.callVoid(DEPENDENCY, "decrementing pending requests in " + h3Cell,
                    () -> client.decrementPendingRequest(h3Cell));
        } catch (RuntimeException e) {
            log.warn("Surge pending-request decrement for cell {} failed; the multiplier will "
                    + "over-report demand until SPS's counter expires", h3Cell, e);
        }
    }
}


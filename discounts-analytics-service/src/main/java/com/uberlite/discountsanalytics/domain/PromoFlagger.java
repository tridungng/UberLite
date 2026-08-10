package com.uberlite.discountsanalytics.domain;

import com.uberlite.common.dto.RiderTripCountDto;
import com.uberlite.discountsanalytics.config.PromoBatchProperties;
import com.uberlite.discountsanalytics.repository.PromoCandidateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Nightly batch that flags low-mileage riders as promotion candidates (ARCHITECTURE.md Sec. 2:
 * "flags riders below a ride-count threshold for a promo, writes rows DPS reads").
 *
 * <h2>Not wired into the live evaluator — on purpose</h2>
 *
 * <p>This populates {@code promo_candidates} and stops there. Discounts &amp; Promotions' rule
 * evaluator still decides discounts from the {@code riderTripCount} passed on the pricing request,
 * and extending it to consult this table is a separate issue. Doing it here would put a nightly
 * batch's output on the synchronous pricing path without a spec for what happens when the batch
 * has never run.
 *
 * <h2>Where personalisation would plug in</h2>
 *
 * <p>The paper's Sec. 5 service is a personalised promotion strategy. {@link #selectCandidates} is
 * the seam: it maps trip counts to rider ids today, and a model scoring churn risk would replace
 * exactly that method, leaving the schedule, the sweep and the table untouched.
 */
@Service
public class PromoFlagger {

    private static final Logger log = LoggerFactory.getLogger(PromoFlagger.class);

    private final RiderTripCountSource tripCounts;
    private final PromoCandidateRepository candidates;
    private final PromoBatchProperties properties;
    private final Clock clock;

    public PromoFlagger(RiderTripCountSource tripCounts,
                        PromoCandidateRepository candidates,
                        PromoBatchProperties properties,
                        Clock clock) {
        this.tripCounts = tripCounts;
        this.candidates = candidates;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * The rule, isolated from the schedule and the database so it can be tested against a plain
     * list.
     *
     * <p>Strictly-less-than: a threshold of 3 means "has not yet taken their third ride", matching
     * the "first 3 rides" promotion. Using {@code <=} would give a fourth discounted ride.
     *
     * @return the rider ids to flag, in input order
     */
    public List<String> selectCandidates(List<RiderTripCountDto> counts) {
        return counts.stream()
                .filter(rider -> rider.getCompletedTrips() < properties.tripThreshold())
                .map(RiderTripCountDto::getRiderId)
                .toList();
    }

    /**
     * Runs the batch.
     *
     * <p>Exposed as a public method rather than living inside the {@code @Scheduled} hook so it can
     * be invoked directly from a test or an operator endpoint without waiting for the cron.
     *
     * @return how many riders are flagged as of this run
     */
    @Transactional
    public int flagCandidates() {
        Instant runAt = clock.instant();

        // Fails the whole run if Trip Service is unreachable, leaving the previous night's
        // candidates in place. Deliberate: a partial refresh followed by the stale sweep below
        // would revoke promotions from riders who still qualify.
        List<RiderTripCountDto> counts = tripCounts.completedTripCounts();
        List<String> selected = selectCandidates(counts);

        selected.forEach(riderId -> candidates.flag(riderId, runAt));

        // Anything not re-flagged this run has crossed the threshold (or no longer exists), so the
        // sweep is what makes the promotion end rather than persist forever.
        int removed = candidates.deleteFlaggedBefore(runAt);

        log.info("Promo batch: {} riders evaluated, {} flagged, {} no longer eligible",
                counts.size(), selected.size(), removed);
        return selected.size();
    }

    /**
     * Riders with <em>zero</em> completed trips never appear in Trip Service's aggregate — a
     * {@code GROUP BY} over completed trips cannot produce a row for someone who has none — so a
     * brand-new rider is not flagged here. That is correct for the MVP: Discounts &amp; Promotions
     * already gives them the new-rider rule from their live {@code riderTripCount}, and inventing
     * candidates would require a rider registry no service owns yet.
     */
    @Scheduled(cron = "${discounts-analytics.cron}")
    public void scheduledRun() {
        try {
            flagCandidates();
        } catch (RuntimeException ex) {
            // A scheduled method that throws kills nothing but the log line; catching here keeps the
            // stack trace attributable to the batch rather than to Spring's scheduler internals.
            log.error("Promo batch failed; yesterday's candidates remain in place", ex);
        }
    }
}

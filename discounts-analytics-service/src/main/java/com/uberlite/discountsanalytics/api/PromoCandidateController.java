package com.uberlite.discountsanalytics.api;

import com.uberlite.discountsanalytics.api.dto.PromoCandidateDto;
import com.uberlite.discountsanalytics.domain.PromoFlagger;
import com.uberlite.discountsanalytics.repository.PromoCandidateEntity;
import com.uberlite.discountsanalytics.repository.PromoCandidateRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Debugging surface for the nightly batch. Nothing in the marketplace calls this — Discounts &amp;
 * Promotions still evaluates rules from the live request (see {@link PromoFlagger}).
 */
@RestController
public class PromoCandidateController {

    private final PromoCandidateRepository candidates;
    private final PromoFlagger flagger;

    public PromoCandidateController(PromoCandidateRepository candidates, PromoFlagger flagger) {
        this.candidates = candidates;
        this.flagger = flagger;
    }

    @GetMapping("/promo-candidates")
    @Transactional(readOnly = true)
    public List<PromoCandidateDto> list() {
        return candidates.findAllByOrderByRiderIdAsc().stream()
                .map(c -> new PromoCandidateDto(c.getRiderId(), c.getFlaggedAt()))
                .toList();
    }

    /**
     * Runs the batch now instead of at the cron time.
     *
     * <p>Exists because a nightly job is otherwise unobservable during a demo or a smoke test: the
     * alternative is editing the cron and restarting. It is idempotent, which is what makes it safe
     * to expose.
     */
    @PostMapping("/promo-candidates/refresh")
    public ResponseEntity<Map<String, Integer>> refresh() {
        return ResponseEntity.ok(Map.of("flagged", flagger.flagCandidates()));
    }
}


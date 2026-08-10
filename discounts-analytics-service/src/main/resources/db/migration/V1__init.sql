-- Discounts Analytics store (ARCHITECTURE.md Sec. 2, background services).
--
-- Its own database rather than a second schema inside discountsdb: ARCHITECTURE.md Sec. 5 says
-- "one schema per service — no shared DB", and sharing would let a nightly batch's table scan
-- compete for connections with the synchronous discount evaluation that sits on the pricing path.
-- Discounts & Promotions will read these candidates over HTTP when issue 06's evaluator is
-- extended, not by reaching into this table.
CREATE TABLE IF NOT EXISTS promo_candidates (
    -- The rider is the natural key: being a candidate is a state, not an event log. Re-running the
    -- batch refreshes flagged_at instead of accumulating a row per night.
    rider_id   VARCHAR(64) PRIMARY KEY,
    flagged_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_promo_candidates_flagged_at ON promo_candidates (flagged_at);


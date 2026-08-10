-- Issue 09: full orchestration (Price Estimation + Matching + Surge pending-requests).
--
-- Adds the state the orchestrator needs to survive a restart mid-trip:
--   * the quote we actually committed to (amount alone is not enough to explain a fare to a rider)
--   * the drivers who already declined, so Matching's stateless answer can be filtered
--   * whether this trip's pending-request has been counted, so the surge counter cannot be
--     double-incremented on a retried quote or double-decremented on a re-sent terminal transition
--   * an optimistic-locking version, because a transition can now be driven both by the rider's
--     request and by the orchestrator's auto-transition

ALTER TABLE trip.trips ADD COLUMN IF NOT EXISTS quote_currency VARCHAR(8);
ALTER TABLE trip.trips ADD COLUMN IF NOT EXISTS quote_breakdown TEXT;
ALTER TABLE trip.trips ADD COLUMN IF NOT EXISTS declined_driver_ids TEXT NOT NULL DEFAULT '[]';
ALTER TABLE trip.trips ADD COLUMN IF NOT EXISTS surge_pending_registered BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE trip.trips ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Operational queries ("what is stuck in the matching pipeline?") scan by state.
CREATE INDEX IF NOT EXISTS idx_trips_state ON trip.trips (state);


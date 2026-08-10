-- Forecasting Service store (ARCHITECTURE.md Sec. 2, background services).
--
-- Demand is bucketed by (cell, hour-of-day, calendar day) rather than stored as raw events: the
-- rolling average only ever reads aggregates, and one row per trip request would grow without
-- bound for a figure that is never queried at that resolution.
CREATE TABLE IF NOT EXISTS demand_counts (
    h3_cell     VARCHAR(32) NOT NULL,
    hour_of_day SMALLINT    NOT NULL CHECK (hour_of_day BETWEEN 0 AND 23),
    day_bucket  DATE        NOT NULL,
    count       BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (h3_cell, hour_of_day, day_bucket)
);

-- The forecast query is always "one cell + one hour, most recent N days", so the primary key's
-- leading columns already serve it; this index only helps the retention sweep.
CREATE INDEX IF NOT EXISTS idx_demand_counts_day_bucket ON demand_counts (day_bucket);

-- Kafka delivery is at-least-once, so a redelivered `REQUESTED` event must not inflate demand.
-- One row per counted trip makes the increment idempotent on the trip id, which is the natural
-- business key: a trip is requested exactly once.
CREATE TABLE IF NOT EXISTS counted_trips (
    trip_id    VARCHAR(64) PRIMARY KEY,
    counted_at TIMESTAMPTZ NOT NULL
);


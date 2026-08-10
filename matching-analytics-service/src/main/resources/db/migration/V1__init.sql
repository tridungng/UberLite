-- Matching Analytics store (ARCHITECTURE.md Sec. 2: "logging only, no training loop").
--
-- One row per matching outcome. The surrogate id exists only so JPA has a simple identifier; the
-- business key is the four columns below, which is why they carry the uniqueness constraint.
CREATE TABLE IF NOT EXISTS match_log (
    id          BIGSERIAL   PRIMARY KEY,
    trip_id     VARCHAR(64) NOT NULL,
    driver_id   VARCHAR(64) NOT NULL,
    outcome     VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    -- Kafka is at-least-once, so the same transition can arrive twice. A trip cannot legitimately
    -- produce two identical (driver, outcome, instant) rows, so this constraint turns a redelivery
    -- into a no-op instead of a duplicate that would skew any decline-rate computed from the table.
    CONSTRAINT uq_match_log_event UNIQUE (trip_id, driver_id, outcome, occurred_at)
);

-- Backs GET /match-log/{tripId}, the only read path.
CREATE INDEX IF NOT EXISTS idx_match_log_trip_id ON match_log (trip_id, occurred_at);

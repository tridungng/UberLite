CREATE SCHEMA IF NOT EXISTS trip;

CREATE TABLE IF NOT EXISTS trip.trips (
    id UUID PRIMARY KEY,
    rider_id VARCHAR(128) NOT NULL,
    state VARCHAR(64) NOT NULL,
    pickup_lat DOUBLE PRECISION NOT NULL,
    pickup_lon DOUBLE PRECISION NOT NULL,
    pickup_h3 VARCHAR(32) NOT NULL,
    dropoff_lat DOUBLE PRECISION NOT NULL,
    dropoff_lon DOUBLE PRECISION NOT NULL,
    dropoff_h3 VARCHAR(32) NOT NULL,
    quoted_price NUMERIC(19, 2),
    driver_id VARCHAR(128),
    attempt_count INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS trip.trip_state_history (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES trip.trips(id) ON DELETE CASCADE,
    from_state VARCHAR(64),
    to_state VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload TEXT NOT NULL
);

CREATE INDEX idx_trip_state_history_trip_id_occurred_at
    ON trip.trip_state_history (trip_id, occurred_at, id);

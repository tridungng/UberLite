-- Create promo_rules table
CREATE TABLE IF NOT EXISTS promo_rules (
    id VARCHAR(64) PRIMARY KEY,
    description TEXT NOT NULL,
    discount_pct NUMERIC(5,4) NOT NULL,
    condition_json JSONB NOT NULL
);

-- Seed: new rider first 3 trips => 20% off
INSERT INTO promo_rules(id, description, discount_pct, condition_json) VALUES
  ('new-rider-first3', 'New rider first 3 trips', 0.20, '{"type":"NEW_RIDER_TRIP_COUNT_LT","value":3}')
ON CONFLICT (id) DO NOTHING;

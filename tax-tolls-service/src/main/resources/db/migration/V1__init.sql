-- Create tax_rates and toll_segments tables
CREATE TABLE IF NOT EXISTS tax_rates (
    region_id VARCHAR(32) PRIMARY KEY,
    rate NUMERIC(5,4) NOT NULL
);

CREATE TABLE IF NOT EXISTS toll_segments (
    route_id VARCHAR(64) PRIMARY KEY,
    amount NUMERIC(8,2) NOT NULL
);

-- Seed data
INSERT INTO tax_rates(region_id, rate) VALUES
  ('CA', 0.0725),
  ('NY', 0.0800),
  ('TX', 0.0625),
  ('WA', 0.1025)
ON CONFLICT (region_id) DO NOTHING;

INSERT INTO toll_segments(route_id, amount) VALUES
  ('CA-DEFAULT', 2.50),
  ('NY-DEFAULT', 5.00),
  ('TX-DEFAULT', 0.00),
  ('WA-DEFAULT', 1.75)
ON CONFLICT (route_id) DO NOTHING;

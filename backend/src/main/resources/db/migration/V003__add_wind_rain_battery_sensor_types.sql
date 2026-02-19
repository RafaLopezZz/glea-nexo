-- V003__add_wind_rain_battery_sensor_types.sqlgnea
-- Add sensor types: WIND, RAIN, BATTERY + required units

-- 1) Units (idempotent upsert)
INSERT INTO unit (code, name, symbol)
VALUES
  ('M_PER_S', 'Meters per second', 'm/s'),
  ('MM',      'Millimeter',       'mm'),
  ('VOLT',    'Volt',             'V')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    symbol = EXCLUDED.symbol;

-- 2) Sensor types (idempotent upsert)
INSERT INTO sensor_type (code, name, default_unit_id)
VALUES
  ('WIND',    'Wind speed', (SELECT id FROM unit WHERE code = 'M_PER_S')),
  ('RAIN',    'Rain',       (SELECT id FROM unit WHERE code = 'MM')),
  ('BATTERY', 'Battery',    (SELECT id FROM unit WHERE code = 'VOLT'))
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    default_unit_id = EXCLUDED.default_unit_id;

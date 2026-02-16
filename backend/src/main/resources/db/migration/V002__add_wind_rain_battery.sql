-- V002__add_wind_rain_battery.sql
-- Añade unidades y tipos para WIND / RAIN / BATTERY (idempotente).

BEGIN;

-- 1) Unidades faltantes
INSERT INTO unit (code, name, symbol)
VALUES
  ('M_S', 'Metros por segundo', 'm/s'),
  ('MM',  'Milímetros',         'mm')
ON CONFLICT (code) DO NOTHING;

-- 2) Tipos faltantes (con default_unit_id)
INSERT INTO sensor_type (code, name, default_unit_id)
VALUES ('WIND', 'Velocidad del viento', (SELECT id FROM unit WHERE code = 'M_S'))
ON CONFLICT (code) DO NOTHING;

INSERT INTO sensor_type (code, name, default_unit_id)
VALUES ('RAIN', 'Lluvia', (SELECT id FROM unit WHERE code = 'MM'))
ON CONFLICT (code) DO NOTHING;

INSERT INTO sensor_type (code, name, default_unit_id)
VALUES ('BATTERY', 'Voltaje de batería', (SELECT id FROM unit WHERE code = 'VOLTS'))
ON CONFLICT (code) DO NOTHING;

COMMIT;

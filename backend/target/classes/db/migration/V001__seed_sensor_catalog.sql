-- Catálogo tipos de sensor
INSERT INTO sensor_type (id, code, name, description) VALUES
(1, 'TEMPERATURE', 'Temperatura', 'Sensor temperatura ambiente o suelo'),
(2, 'SOIL_MOISTURE', 'Humedad Suelo', 'Sensor capacitivo humedad volumétrica'),
(3, 'HUMIDITY', 'Humedad Ambiente', 'Humedad relativa del aire'),
(4, 'PH', 'pH Suelo', 'Medidor acidez/alcalinidad'),
(5, 'EC', 'Conductividad Eléctrica', 'Nutrientes en solución'),
(6, 'LIGHT', 'Luz', 'Sensor luminosidad (lux o PAR)'),
(7, 'PRESSURE', 'Presión Atmosférica', 'Barómetro'),
(8, 'GPS', 'Posicionamiento', 'Coordenadas GPS ganado/maquinaria')
ON CONFLICT (code) DO NOTHING;

-- Catálogo unidades
INSERT INTO unit (id, code, symbol, name) VALUES
(1, 'CELSIUS', '°C', 'Grados Celsius'),
(2, 'FAHRENHEIT', '°F', 'Grados Fahrenheit'),
(3, 'PERCENT', '%', 'Porcentaje'),
(4, 'PH_SCALE', 'pH', 'Escala pH'),
(5, 'MS_CM', 'mS/cm', 'MiliSiemens por centímetro'),
(6, 'LUX', 'lx', 'Lux'),
(7, 'HPA', 'hPa', 'Hectopascales'),
(8, 'DBM', 'dBm', 'Decibel miliwatt'),
(9, 'VOLTS', 'V', 'Voltios')
ON CONFLICT (code) DO NOTHING;

-- Secuencias para evitar conflictos en inserts futuros
SELECT setval('sensor_type_id_seq', 100);
SELECT setval('unit_id_seq', 100);
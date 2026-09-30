-- Conserva los identificadores y permisos existentes; actualiza las tarifas.
UPDATE planes SET precio_mensual = 10.00, precio_semestral = 60.00 WHERE nombre = 'pro';
UPDATE planes SET precio_mensual = 20.00, precio_semestral = 120.00 WHERE nombre = 'elite';

-- Mantiene los identificadores internos pro/elite para las suscripciones existentes.
-- 32767 representa capacidad sin límite práctico dentro del esquema SMALLINT.
UPDATE planes SET precio_mensual = 15.00, precio_semestral = 75.00,
  limite_catalogo = 10, incluye_video = FALSE, incluye_analitica_avanzada = FALSE,
  incluye_multiusuario = FALSE, incluye_traduccion = FALSE, incluye_certificado_pdf = FALSE
WHERE nombre = 'pro';

UPDATE planes SET precio_mensual = 25.00, precio_semestral = 125.00,
  limite_catalogo = 32767, incluye_video = TRUE, incluye_analitica_avanzada = TRUE,
  incluye_multiusuario = TRUE, incluye_traduccion = TRUE, incluye_certificado_pdf = TRUE,
  incluye_whatsapp_business_api = FALSE
WHERE nombre = 'elite';

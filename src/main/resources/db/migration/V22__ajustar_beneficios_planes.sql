-- La oferta vigente de Básico y Plus se limita a perfil, medios, sellos,
-- chat y estadísticas. La traducción y las cuentas colaboradoras salen de la oferta.
UPDATE planes
SET incluye_traduccion = FALSE,
    incluye_multiusuario = FALSE
WHERE nombre IN ('pro', 'elite');

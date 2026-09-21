-- Traducción manual del catálogo (B9.1) — self-service, no automática: no
-- hay integración con un traductor real (requeriría una API paga). El
-- propio emprendedor escribe su versión en inglés, solo en planes con
-- incluye_traduccion.
ALTER TABLE catalogo_items ADD COLUMN nombre_en VARCHAR(120);

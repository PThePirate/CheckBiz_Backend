-- Los negocios sin los seis requisitos de Verificado quedan pendientes.
-- Las filas existentes del checklist conservan su historial al reagruparse.
ALTER TABLE negocios DROP CONSTRAINT IF EXISTS negocios_nivel_formalizacion_check;
ALTER TABLE ruta_formalizacion DROP CONSTRAINT IF EXISTS ruta_formalizacion_nivel_check;

UPDATE negocios SET nivel_formalizacion = 'pendiente'
WHERE nivel_formalizacion IN ('semilla', 'asesoria');

UPDATE ruta_formalizacion SET nivel = 'verificado'
WHERE nivel IN ('semilla', 'asesoria');

ALTER TABLE negocios ALTER COLUMN nivel_formalizacion SET DEFAULT 'pendiente';
ALTER TABLE negocios ADD CONSTRAINT negocios_nivel_formalizacion_check
  CHECK (nivel_formalizacion IN ('pendiente', 'verificado', 'formalizado'));
ALTER TABLE ruta_formalizacion ADD CONSTRAINT ruta_formalizacion_nivel_check
  CHECK (nivel IN ('verificado', 'formalizado'));

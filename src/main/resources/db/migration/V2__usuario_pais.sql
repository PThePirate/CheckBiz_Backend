-- Las cuentas del piloto existentes usan Ecuador como país del teléfono.
ALTER TABLE usuarios ADD COLUMN pais VARCHAR(2) NOT NULL DEFAULT 'EC';
ALTER TABLE usuarios ADD CONSTRAINT usuarios_pais_check CHECK (pais IN ('EC', 'CO', 'MX', 'US'));

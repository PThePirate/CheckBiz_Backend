ALTER TABLE insignias DROP CONSTRAINT insignias_tipo_check;
ALTER TABLE insignias ADD CONSTRAINT insignias_tipo_check
    CHECK (tipo IN ('usuario', 'negocio', 'co-branded'));

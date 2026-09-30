CREATE TABLE solicitudes_reunion (
    id UUID PRIMARY KEY,
    correo VARCHAR(254) NOT NULL,
    datos JSONB NOT NULL,
    estado VARCHAR(30) NOT NULL,
    privacidad_version VARCHAR(30) NOT NULL DEFAULT 'reunion-v1',
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

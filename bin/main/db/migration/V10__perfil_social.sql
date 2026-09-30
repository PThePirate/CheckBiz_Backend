ALTER TABLE usuarios
    ADD COLUMN nombre_usuario VARCHAR(24),
    ADD COLUMN descripcion_perfil VARCHAR(300),
    ADD COLUMN estado_perfil VARCHAR(80);

CREATE UNIQUE INDEX usuarios_nombre_usuario_unico
    ON usuarios (LOWER(nombre_usuario)) WHERE nombre_usuario IS NOT NULL;

CREATE TABLE fotos_perfil_recientes (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    foto_url TEXT NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX fotos_perfil_recientes_usuario_fecha
    ON fotos_perfil_recientes (usuario_id, creado_en DESC);

INSERT INTO fotos_perfil_recientes (id, usuario_id, foto_url, creado_en)
SELECT gen_random_uuid(), id, foto_perfil_url, actualizado_en
FROM usuarios WHERE foto_perfil_url IS NOT NULL;

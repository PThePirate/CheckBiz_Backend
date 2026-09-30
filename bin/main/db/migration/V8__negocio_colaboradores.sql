-- B9.1 (Elite) — multiusuario: un negocio puede tener colaboradores además
-- de su dueño. Un usuario solo puede ser colaborador de un negocio a la vez
-- y nunca de uno que ya es suyo (se valida en el backend, no aquí).
CREATE TABLE negocio_colaboradores (
    negocio_id UUID NOT NULL REFERENCES negocios(id) ON DELETE CASCADE,
    usuario_id UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    agregado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (negocio_id, usuario_id),
    UNIQUE (usuario_id)
);

-- Chat interno de una solicitud (reemplaza el botón de WhatsApp): cada
-- solicitud pasa a tener una conversación real de mensajes en vez de una
-- sola descripción fija. La descripción original de cada solicitud ya
-- existente se preserva como su primer mensaje para no perder historial.
CREATE TABLE mensajes_solicitud (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    solicitud_id  UUID NOT NULL REFERENCES solicitudes(id) ON DELETE CASCADE,
    autor_id      UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    cuerpo        TEXT NOT NULL,
    creado_en     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_mensajes_solicitud_solicitud ON mensajes_solicitud(solicitud_id, creado_en);

INSERT INTO mensajes_solicitud (solicitud_id, autor_id, cuerpo, creado_en)
SELECT id, cliente_id, descripcion, creado_en FROM solicitudes;

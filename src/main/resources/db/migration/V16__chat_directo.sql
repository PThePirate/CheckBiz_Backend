ALTER TABLE analitica_eventos DROP CONSTRAINT analitica_eventos_tipo_evento_check;
ALTER TABLE analitica_eventos ADD CONSTRAINT analitica_eventos_tipo_evento_check
    CHECK (tipo_evento IN ('visita_perfil', 'clic_whatsapp', 'contacto_chat', 'clic_solicitud', 'escaneo_qr'));

ALTER TABLE solicitudes ADD COLUMN cliente_oculto BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE solicitudes ADD COLUMN negocio_oculto BOOLEAN NOT NULL DEFAULT FALSE;

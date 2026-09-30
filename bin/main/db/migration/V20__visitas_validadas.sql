ALTER TABLE analitica_eventos ADD COLUMN visitante_id UUID;
ALTER TABLE analitica_eventos ADD COLUMN dia_visita DATE;
ALTER TABLE analitica_eventos DROP CONSTRAINT analitica_eventos_tipo_evento_check;
ALTER TABLE analitica_eventos ADD CONSTRAINT analitica_eventos_tipo_evento_check CHECK (tipo_evento IN ('visita_perfil','visita_validada','clic_whatsapp','contacto_chat','clic_solicitud','escaneo_qr'));
CREATE UNIQUE INDEX idx_visita_validada_unica ON analitica_eventos(negocio_id, visitante_id, dia_visita) WHERE tipo_evento = 'visita_validada';

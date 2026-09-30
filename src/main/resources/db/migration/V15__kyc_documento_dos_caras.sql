-- El KYC documental requiere ambas caras. Los registros antiguos de selfie
-- quedan identificados por reverso NULL y no pueden aprobarse como documento.
ALTER TABLE verificaciones_foto ADD COLUMN foto_reverso_url TEXT;

-- La antigua selfie con cédula no acredita la revisión de ambas caras.
-- Tampoco se conserva el nivel 4 asignado por el cruce SENESCYT/SRI simulado.
UPDATE usuarios SET foto_verificacion_estado = 'no_iniciada'
WHERE foto_verificacion_estado = 'aprobada'
  AND id IN (SELECT usuario_id FROM verificaciones_foto WHERE foto_reverso_url IS NULL);
UPDATE usuarios SET kyc_layer = LEAST(kyc_layer, 2), senescyt_sri_estado = 'no_verificado'
WHERE kyc_layer > 2 OR senescyt_sri_estado = 'verificado';

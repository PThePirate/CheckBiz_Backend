-- Eliminar cuenta (B12) es una desactivación, no un DELETE: un usuario
-- puede tener negocios, reseñas y solicitudes que otras personas siguen
-- necesitando ver. "Eliminada" reutiliza el mismo corte de sesión que ya
-- existe para "vetada" — el filtro JWT bloquea cualquier estado distinto
-- de "activa", no solo el veto.
ALTER TABLE usuarios DROP CONSTRAINT usuarios_estado_cedula_check;
ALTER TABLE usuarios ADD CONSTRAINT usuarios_estado_cedula_check
    CHECK (estado_cedula IN ('activa', 'vetada', 'eliminada'));

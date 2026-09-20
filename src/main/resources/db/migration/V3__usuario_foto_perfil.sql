-- Foto de perfil del usuario (A9). Distinta de verificaciones_foto (KYC,
-- sensible y revisada manualmente): esta es solo un avatar de bajo riesgo
-- que el propio dueño sube y reemplaza cuando quiera, sin revisión.
ALTER TABLE usuarios ADD COLUMN foto_perfil_url TEXT;

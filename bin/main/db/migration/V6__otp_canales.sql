-- Corrige un bug real preexistente: el código ya envía la OTP de Capa 2 por
-- correo (canal='email', desde que se migró de SMS a Resend) y esta misma
-- sesión agrega el canal 'recuperacion' (B12), pero el CHECK seguía
-- limitado a ('sms','whatsapp') desde V1 — cualquier envío con canal='email'
-- rompía el registro/login con un 500 disfrazado de "DUPLICADO". Nunca se
-- notó porque los usuarios de prueba ya tenían su OTP verificado de antes.
ALTER TABLE otp_verificaciones DROP CONSTRAINT otp_verificaciones_canal_check;
ALTER TABLE otp_verificaciones ALTER COLUMN canal TYPE VARCHAR(20);
ALTER TABLE otp_verificaciones ADD CONSTRAINT otp_verificaciones_canal_check
    CHECK (canal IN ('sms', 'whatsapp', 'email', 'recuperacion'));

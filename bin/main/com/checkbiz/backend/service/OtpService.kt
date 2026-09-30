package com.checkbiz.backend.service

import com.checkbiz.backend.domain.OtpVerificacion
import com.checkbiz.backend.domain.Usuario
import com.checkbiz.backend.exception.AppException
import com.checkbiz.backend.repository.OtpVerificacionRepository
import com.checkbiz.backend.repository.UsuarioRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID
import java.security.SecureRandom

@Service
class OtpService(
    private val otpRepository: OtpVerificacionRepository,
    private val usuarioRepository: UsuarioRepository,
    private val emailService: EmailService,
    @Value("\${checkbiz.otp.dev-mode}") private val devMode: Boolean,
) {
    private val random = SecureRandom()

    companion object {
        private const val TTL_MINUTOS = 5L
        private const val MAX_INTENTOS = 5
    }

    data class EnvioResultado(val expiraEn: OffsetDateTime, val codigoDev: String?)

    @Transactional
    fun enviar(usuario: Usuario, canal: String): EnvioResultado {
        val codigo = (100_000 + random.nextInt(900_000)).toString()
        val expiraEn = OffsetDateTime.now().plusMinutes(TTL_MINUTOS)

        otpRepository.save(
            OtpVerificacion(usuario = usuario, codigo = codigo, canal = canal, expiraEn = expiraEn)
        )

        if (devMode) {
            // Modo desarrollo: no se envía correo real — el código vuelve en
            // la respuesta para poder probar el flujo sin un proveedor configurado.
            return EnvioResultado(expiraEn = expiraEn, codigoDev = codigo)
        }

        // Producción: correo real vía Resend (Capa 2 — antes era SMS al
        // teléfono, ahora es un código enviado al correo del usuario).
        emailService.enviar(
            destinatario = usuario.correo,
            asunto = "Tu código de verificación CheckBiz",
            cuerpoHtml = EmailTemplates.codigoVerificacion(
                nombreCompleto = usuario.nombreCompleto,
                etiqueta = "Verificación de cuenta",
                mensaje = "Usa este código para confirmar tu correo y continuar con tu registro en CheckBiz.",
                codigo = codigo,
            ),
        )

        return EnvioResultado(expiraEn = expiraEn, codigoDev = null)
    }

    @Transactional
    fun verificar(usuarioId: UUID, codigo: String) {
        val otp = otpRepository
            .findFirstByUsuarioIdAndVerificadoFalseOrderByCreadoEnDesc(usuarioId)
            .orElseThrow {
                AppException(HttpStatus.BAD_REQUEST, "OTP_NO_ENCONTRADO", "No hay un código pendiente. Solicita uno nuevo.")
            }

        if (otp.expiraEn.isBefore(OffsetDateTime.now())) {
            throw AppException(HttpStatus.BAD_REQUEST, "OTP_EXPIRADO", "El código expiró. Solicita uno nuevo.")
        }
        if (otp.intentos >= MAX_INTENTOS) {
            throw AppException(HttpStatus.TOO_MANY_REQUESTS, "OTP_MAX_INTENTOS", "Demasiados intentos. Solicita un código nuevo.")
        }

        if (otp.codigo != codigo) {
            otp.intentos = (otp.intentos + 1).toShort()
            otpRepository.save(otp)
            throw AppException(HttpStatus.BAD_REQUEST, "OTP_INCORRECTO", "El código no es correcto")
        }

        otp.verificado = true
        otpRepository.save(otp)

        val usuario = usuarioRepository.findById(usuarioId).orElseThrow()
        if (usuario.kycLayer.toInt() == 1) {
            usuario.kycLayer = 2
            usuario.actualizadoEn = OffsetDateTime.now()
            usuarioRepository.save(usuario)
        }
    }

    // ===================================================================
    // Recuperación de contraseña (B12) — canal propio, nunca toca kycLayer.
    // ===================================================================
    @Transactional
    fun enviarRecuperacion(usuario: Usuario): EnvioResultado {
        val codigo = (100_000 + random.nextInt(900_000)).toString()
        val expiraEn = OffsetDateTime.now().plusMinutes(TTL_MINUTOS)

        otpRepository.save(
            OtpVerificacion(usuario = usuario, codigo = codigo, canal = "recuperacion", expiraEn = expiraEn)
        )

        if (devMode) {
            return EnvioResultado(expiraEn = expiraEn, codigoDev = codigo)
        }

        emailService.enviar(
            destinatario = usuario.correo,
            asunto = "Recupera tu contraseña de CheckBiz",
            cuerpoHtml = EmailTemplates.codigoVerificacion(
                nombreCompleto = usuario.nombreCompleto,
                etiqueta = "Recuperar contraseña",
                mensaje = "Usa este código para restablecer tu contraseña. Si no fuiste tú, tu contraseña actual sigue siendo válida.",
                codigo = codigo,
            ),
        )
        return EnvioResultado(expiraEn = expiraEn, codigoDev = null)
    }

    @Transactional
    fun verificarRecuperacion(usuarioId: UUID, codigo: String) {
        val otp = otpRepository
            .findFirstByUsuarioIdAndCanalAndVerificadoFalseOrderByCreadoEnDesc(usuarioId, "recuperacion")
            .orElseThrow {
                AppException(HttpStatus.BAD_REQUEST, "OTP_NO_ENCONTRADO", "No hay un código de recuperación pendiente. Solicita uno nuevo.")
            }

        if (otp.expiraEn.isBefore(OffsetDateTime.now())) {
            throw AppException(HttpStatus.BAD_REQUEST, "OTP_EXPIRADO", "El código expiró. Solicita uno nuevo.")
        }
        if (otp.intentos >= MAX_INTENTOS) {
            throw AppException(HttpStatus.TOO_MANY_REQUESTS, "OTP_MAX_INTENTOS", "Demasiados intentos. Solicita un código nuevo.")
        }
        if (otp.codigo != codigo) {
            otp.intentos = (otp.intentos + 1).toShort()
            otpRepository.save(otp)
            throw AppException(HttpStatus.BAD_REQUEST, "OTP_INCORRECTO", "El código no es correcto")
        }

        otp.verificado = true
        otpRepository.save(otp)
    }
}
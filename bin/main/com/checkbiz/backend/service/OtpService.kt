package com.checkbiz.backend.service

import com.checkbiz.backend.domain.OtpVerificacion
import com.checkbiz.backend.domain.Usuario
import com.checkbiz.backend.exception.AppException
import com.checkbiz.backend.repository.OtpVerificacionRepository
import com.checkbiz.backend.repository.UsuarioRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.random.Random

@Service
class OtpService(
    private val otpRepository: OtpVerificacionRepository,
    private val usuarioRepository: UsuarioRepository,
    @Value("\${checkbiz.otp.dev-mode}") private val devMode: Boolean,
) {
    private val log = LoggerFactory.getLogger(OtpService::class.java)

    companion object {
        private const val TTL_MINUTOS = 5L
        private const val MAX_INTENTOS = 5
    }

    data class EnvioResultado(val expiraEn: OffsetDateTime, val codigoDev: String?)

    @Transactional
    fun enviar(usuario: Usuario, canal: String): EnvioResultado {
        val codigo = Random.nextInt(100_000, 1_000_000).toString()
        val expiraEn = OffsetDateTime.now().plusMinutes(TTL_MINUTOS)

        otpRepository.save(
            OtpVerificacion(usuario = usuario, codigo = codigo, canal = canal, expiraEn = expiraEn)
        )

        // TODO: integrar proveedor real (Twilio / WhatsApp Business API).
        log.info("[OTP] Código para usuario {} ({}): {}", usuario.id, canal, codigo)

        return EnvioResultado(expiraEn = expiraEn, codigoDev = if (devMode) codigo else null)
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
}

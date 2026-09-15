package com.checkbiz.backend.service

import com.checkbiz.backend.config.JwtService
import com.checkbiz.backend.domain.ContratoAdhesion
import com.checkbiz.backend.dto.ActivarEmprendedorResponse
import com.checkbiz.backend.exception.AppException
import com.checkbiz.backend.repository.ContratoAdhesionRepository
import com.checkbiz.backend.repository.UsuarioRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
class EmprendedorService(
    private val usuarioRepository: UsuarioRepository,
    private val contratoRepository: ContratoAdhesionRepository,
    private val jwtService: JwtService,
) {
    /**
     * Simula el cruce con SENESCYT/SRI (Capa 4). En un entorno real esto
     * llamaría a un servicio externo; aquí se resuelve como "verificado" de
     * forma síncrona para no bloquear la demo.
     * TODO: reemplazar por integración real cuando esté disponible.
     */
    private fun consultarSenescytSri(cedula: String): String = "verificado"

    @Transactional
    fun activar(usuarioId: UUID, ip: String, userAgent: String?): ActivarEmprendedorResponse {
        val usuario = usuarioRepository.findById(usuarioId).orElseThrow()

        if (usuario.rolEmprendedor) {
            throw AppException(HttpStatus.CONFLICT, "YA_ES_EMPRENDEDOR", "Esta cuenta ya tiene el perfil de emprendedor activo")
        }

        if (usuario.fotoVerificacionEstado == "no_iniciada") {
            throw AppException(
                HttpStatus.BAD_REQUEST,
                "FALTA_CAPA_3",
                "Primero debes subir tu foto de verificación (Capa 3) antes de activar tu negocio"
            )
        }

        contratoRepository.save(
            ContratoAdhesion(
                usuario = usuario,
                tipo = "adhesion_emprendedor",
                ipFirma = ip,
                userAgent = userAgent,
            )
        )

        usuario.rolEmprendedor = true
        usuario.senescytSriEstado = consultarSenescytSri(usuario.cedula)
        if (usuario.kycLayer < 4) usuario.kycLayer = 4
        usuario.actualizadoEn = OffsetDateTime.now()
        usuarioRepository.save(usuario)

        return ActivarEmprendedorResponse(
            mensaje = "Perfil de emprendedor activado. Ya puedes crear tu Mini Landing Page.",
            usuario = usuario.aDto(),
            token = jwtService.generarTokenUsuario(usuario.aClaims()),
        )
    }
}

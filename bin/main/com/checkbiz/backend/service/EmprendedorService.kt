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
                "Primero debes enviar las dos caras de tu cédula (Capa 3) antes de activar tu negocio"
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
        // Activar un negocio no completa KYC, biometría ni cruces externos.
        usuario.actualizadoEn = OffsetDateTime.now()
        usuarioRepository.save(usuario)

        return ActivarEmprendedorResponse(
            mensaje = "Perfil de emprendedor activado. Ya puedes crear tu Mini Landing Page.",
            usuario = usuario.aDto(),
            token = jwtService.generarTokenUsuario(usuario.aClaims()),
        )
    }
}

package com.checkbiz.backend.service

import com.checkbiz.backend.domain.Denuncia
import com.checkbiz.backend.dto.CrearDenunciaRequest
import com.checkbiz.backend.dto.DenunciaCreadaResponse
import com.checkbiz.backend.exception.AppException
import com.checkbiz.backend.repository.DenunciaRepository
import com.checkbiz.backend.repository.NegocioRepository
import com.checkbiz.backend.repository.UsuarioRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Denuncias creadas por el CLIENTE (A11). La moderación de estas mismas
 * denuncias (verlas, archivarlas) vive en AdminService — separado a
 * propósito, igual que NegocioController vs NegocioPublicoController:
 * quién puede CREAR una denuncia y quién puede MODERARLA son roles
 * completamente distintos.
 */
@Service
class DenunciaService(
    private val denunciaRepository: DenunciaRepository,
    private val negocioRepository: NegocioRepository,
    private val usuarioRepository: UsuarioRepository,
) {

    @Transactional
    fun crear(reportanteId: UUID, req: CrearDenunciaRequest): DenunciaCreadaResponse {
        val negocio = negocioRepository.findBySlugAndEstadoPublicacion(req.negocioSlug, "publicado")
            ?: throw AppException(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "Este negocio no existe o no está publicado")
        val reportante = usuarioRepository.findById(reportanteId).orElseThrow()
        val propietario = negocio.usuario!!

        val denuncia = denunciaRepository.save(
            Denuncia(
                reportante = reportante,
                cedulaReportada = propietario.cedula,
                motivo = req.motivo,
                estado = "abierta",
            )
        )

        return DenunciaCreadaResponse(id = denuncia.id!!, estado = denuncia.estado, creadoEn = denuncia.creadoEn)
    }
}
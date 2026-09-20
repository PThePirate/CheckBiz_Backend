package com.checkbiz.backend.service

import com.checkbiz.backend.domain.AlumniVerificacion
import com.checkbiz.backend.dto.SolicitarVerificacionAlumniRequest
import com.checkbiz.backend.dto.UniversidadResponse
import com.checkbiz.backend.dto.VerificacionAlumniResponse
import com.checkbiz.backend.exception.AppException
import com.checkbiz.backend.repository.AlumniVerificacionRepository
import com.checkbiz.backend.repository.UniversidadRepository
import com.checkbiz.backend.repository.UsuarioRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Verificación de alumni (C3, lado del usuario): un comprador/emprendedor
 * pide que una universidad lo reconozca como su egresado. La universidad
 * decide desde su propio panel institucional (InstitucionalService) —
 * aquí nunca se aprueba nada, solo se solicita y se consulta el estado.
 */
@Service
class AlumniService(
    private val universidadRepository: UniversidadRepository,
    private val alumniVerificacionRepository: AlumniVerificacionRepository,
    private val usuarioRepository: UsuarioRepository,
) {

    fun listarUniversidades(): List<UniversidadResponse> =
        universidadRepository.findAllByActivaTrueOrderByNombreAsc().map { UniversidadResponse(it.id!!, it.nombre) }

    @Transactional
    fun solicitarVerificacion(usuarioId: UUID, req: SolicitarVerificacionAlumniRequest): VerificacionAlumniResponse {
        val usuario = usuarioRepository.findById(usuarioId).orElseThrow()
        val universidad = universidadRepository.findById(req.universidadId).orElseThrow {
            AppException(HttpStatus.BAD_REQUEST, "UNIVERSIDAD_INVALIDA", "La universidad seleccionada no existe")
        }
        if (alumniVerificacionRepository.findByUsuarioIdAndUniversidadId(usuarioId, req.universidadId) != null) {
            throw AppException(HttpStatus.CONFLICT, "YA_SOLICITADA", "Ya tienes una solicitud con esta universidad")
        }

        val verificacion = alumniVerificacionRepository.save(
            AlumniVerificacion(usuario = usuario, universidad = universidad, estado = "pendiente")
        )
        return verificacion.aResponse()
    }

    @Transactional(readOnly = true)
    fun misVerificaciones(usuarioId: UUID): List<VerificacionAlumniResponse> =
        alumniVerificacionRepository.findByUsuarioIdOrderByCreadoEnDesc(usuarioId).map { it.aResponse() }

    private fun AlumniVerificacion.aResponse() = VerificacionAlumniResponse(
        id = id!!,
        universidad = UniversidadResponse(universidad!!.id!!, universidad!!.nombre),
        estado = estado, creadoEn = creadoEn, verificadoEn = verificadoEn,
    )
}

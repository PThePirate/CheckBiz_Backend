package com.checkbiz.backend.service

import com.checkbiz.backend.domain.Resena
import com.checkbiz.backend.domain.Solicitud
import com.checkbiz.backend.dto.*
import com.checkbiz.backend.exception.AppException
import com.checkbiz.backend.repository.NegocioRepository
import com.checkbiz.backend.repository.ResenaRepository
import com.checkbiz.backend.repository.SolicitudRepository
import com.checkbiz.backend.repository.UsuarioRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
class SolicitudService(
    private val solicitudRepository: SolicitudRepository,
    private val negocioRepository: NegocioRepository,
    private val usuarioRepository: UsuarioRepository,
    private val resenaRepository: ResenaRepository,
    private val negocioService: NegocioService,
) {

    @Transactional
    fun crear(clienteId: UUID, req: CrearSolicitudRequest): SolicitudResponse {
        val negocio = negocioRepository.findBySlugAndEstadoPublicacion(req.negocioSlug, "publicado")
            ?: throw AppException(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "Este negocio no existe o no está publicado")
        val cliente = usuarioRepository.findById(clienteId).orElseThrow()

        val solicitud = solicitudRepository.save(
            Solicitud(
                negocio = negocio, cliente = cliente,
                descripcion = req.descripcion, fechaEstimada = req.fechaEstimada,
                estado = "enviada",
            )
        )
        return solicitud.aResponse()
    }

    @Transactional(readOnly = true)
    fun misSolicitudes(clienteId: UUID): List<SolicitudResponse> =
        solicitudRepository.findByClienteIdOrderByCreadoEnDesc(clienteId).map { it.aResponse() }

    @Transactional
    fun confirmar(clienteId: UUID, solicitudId: UUID): SolicitudResponse {
        val solicitud = miSolicitudOrThrow(clienteId, solicitudId)

        if (solicitud.estado == "confirmada") {
            throw AppException(HttpStatus.CONFLICT, "YA_CONFIRMADA", "Esta solicitud ya fue confirmada")
        }
        if (solicitud.estado == "cancelada") {
            throw AppException(HttpStatus.CONFLICT, "SOLICITUD_CANCELADA", "Esta solicitud fue cancelada")
        }

        solicitud.estado = "confirmada"
        solicitud.confirmadaEn = OffsetDateTime.now()
        solicitud.actualizadoEn = OffsetDateTime.now()
        solicitudRepository.save(solicitud)
        negocioService.recalcularTrustScore(solicitud.negocio!!.id!!)

        return solicitud.aResponse()
    }

    @Transactional
    fun dejarResena(clienteId: UUID, solicitudId: UUID, req: CrearResenaRequest): SolicitudResponse {
        val solicitud = miSolicitudOrThrow(clienteId, solicitudId)

        if (solicitud.estado != "confirmada") {
            throw AppException(
                HttpStatus.BAD_REQUEST, "SIN_CONFIRMAR",
                "Primero debes confirmar que recibiste el producto o servicio"
            )
        }
        if (resenaRepository.existsBySolicitudId(solicitudId)) {
            throw AppException(HttpStatus.CONFLICT, "YA_RESENADA", "Ya dejaste una reseña para esta solicitud")
        }

        resenaRepository.save(
            Resena(
                solicitud = solicitud, negocio = solicitud.negocio, cliente = solicitud.cliente,
                estrellas = req.estrellas.toShort(), comentario = req.comentario,
            )
        )
        negocioService.recalcularTrustScore(solicitud.negocio!!.id!!)

        return solicitud.aResponse()
    }

    // ===================================================================
    private fun miSolicitudOrThrow(clienteId: UUID, solicitudId: UUID): Solicitud {
        val solicitud = solicitudRepository.findById(solicitudId)
            .orElseThrow { AppException(HttpStatus.NOT_FOUND, "NO_ENCONTRADA", "Solicitud no encontrada") }
        if (solicitud.cliente?.id != clienteId) {
            throw AppException(HttpStatus.FORBIDDEN, "NO_AUTORIZADO", "Esta solicitud no te pertenece")
        }
        return solicitud
    }

    private fun Solicitud.aResponse(): SolicitudResponse {
        val resena = id?.let { resenaRepository.findBySolicitudId(it) }
        return SolicitudResponse(
            id = id!!,
            negocio = NegocioMiniResponse(negocio!!.nombreComercial, negocio!!.slug),
            descripcion = descripcion, fechaEstimada = fechaEstimada, estado = estado,
            creadoEn = creadoEn, confirmadaEn = confirmadaEn,
            resena = resena?.let { ResenaResumenResponse(it.id!!, it.estrellas, it.comentario, it.creadoEn) },
        )
    }
}
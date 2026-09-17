package com.checkbiz.backend.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

// ---------------------------------------------------------------------
// Solicitud de pedido (A7)
// ---------------------------------------------------------------------
data class CrearSolicitudRequest(
    @field:NotBlank
    val negocioSlug: String,

    @field:NotBlank @field:Size(min = 5, max = 1000)
    val descripcion: String,

    val fechaEstimada: LocalDate? = null,
)

data class NegocioMiniResponse(val nombreComercial: String, val slug: String)

data class ResenaResumenResponse(
    val id: UUID,
    val estrellas: Short,
    val comentario: String?,
    val creadoEn: OffsetDateTime,
)

data class SolicitudResponse(
    val id: UUID,
    val negocio: NegocioMiniResponse,
    val descripcion: String,
    val fechaEstimada: LocalDate?,
    val estado: String,
    val creadoEn: OffsetDateTime,
    val confirmadaEn: OffsetDateTime?,
    val resena: ResenaResumenResponse?,
)

// ---------------------------------------------------------------------
// Confirmación y reseña (A8)
// ---------------------------------------------------------------------
data class CrearResenaRequest(
    @field:Min(1) @field:Max(5)
    val estrellas: Int,

    @field:Size(max = 500)
    val comentario: String? = null,
)

// ---------------------------------------------------------------------
// Bandeja de solicitudes del emprendedor (B5)
// ---------------------------------------------------------------------
data class ClienteResumenResponse(val nombreCompleto: String, val telefono: String)

data class SolicitudRecibidaResponse(
    val id: UUID,
    val cliente: ClienteResumenResponse,
    val descripcion: String,
    val fechaEstimada: LocalDate?,
    val estado: String,
    val creadoEn: OffsetDateTime,
    val confirmadaEn: OffsetDateTime?,
)

data class ActualizarEstadoSolicitudRequest(
    @field:Pattern(regexp = "en_conversacion|cancelada", message = "Estado no permitido para el emprendedor")
    val estado: String,
)
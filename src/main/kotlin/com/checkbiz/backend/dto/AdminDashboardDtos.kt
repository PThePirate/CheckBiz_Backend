package com.checkbiz.backend.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

// ---------------------------------------------------------------------
// Panel general — estadísticas y actividad
// ---------------------------------------------------------------------
data class EstadisticasResponse(
    val usuariosTotales: Long,
    val usuariosPorCapa: Map<Int, Long>,
    val negociosPublicados: Long,
    val tasaAprobacionKyc: Double,
    val cedulasVetadas: Long,
    val kycPendientes: Long,
)

data class ActividadItemResponse(
    val id: Long,
    val tipo: String,
    val texto: String,
    val fecha: OffsetDateTime,
)

// ---------------------------------------------------------------------
// Búsqueda de usuarios + ficha 360°
// ---------------------------------------------------------------------
data class NegocioResumenResponse(
    val nombreComercial: String,
    val nivelFormalizacion: String,
    val trustScore: Short,
)

data class ContratoResumenResponse(
    val tipo: String,
    val firmadoEn: OffsetDateTime,
    val ip: String,
)

data class UsuarioDetalleResponse(
    val id: UUID,
    val nombreCompleto: String,
    val cedula: String,
    val correo: String,
    val telefono: String,
    val rolCliente: Boolean,
    val rolEmprendedor: Boolean,
    val kycLayer: Short,
    val fotoVerificacionEstado: String,
    val senescytSriEstado: String,
    val estadoCedula: String,
    val creadoEn: OffsetDateTime,
    val negocio: NegocioResumenResponse?,
    val contratos: List<ContratoResumenResponse>,
    val solicitudes: Long,
    val resenas: Long,
)

// ---------------------------------------------------------------------
// Categorías (E5)
// ---------------------------------------------------------------------
data class CategoriaResponse(
    val id: Int,
    val nombre: String,
    val icono: String?,
    val activa: Boolean,
    val negocios: Long,
)

data class CrearCategoriaRequest(
    @field:NotBlank @field:Size(min = 2, max = 60)
    val nombre: String,
    val icono: String? = null,
)

// ---------------------------------------------------------------------
// Denuncias (E3)
// ---------------------------------------------------------------------
data class ReportanteResponse(val nombreCompleto: String, val correo: String)

data class DenunciaResponse(
    val id: UUID,
    val estado: String,
    val reportante: ReportanteResponse,
    val cedulaReportada: String,
    val motivo: String,
    val creadoEn: OffsetDateTime,
)

data class ResolverDenunciaRequest(
    @field:Pattern(regexp = "archivar|vetar")
    val accion: String,
)

// ---------------------------------------------------------------------
// Suscripciones y licenciamiento B2B (E6)
// ---------------------------------------------------------------------
data class SuscripcionPorPlanResponse(val plan: String, val total: Long)

data class InstitucionActivaResponse(
    val nombreInstitucion: String,
    val tipo: String,
    val correo: String,
    val creadoEn: OffsetDateTime,
)

data class GestionSuscripcionesResponse(
    val porPlan: List<SuscripcionPorPlanResponse>,
    val ingresoMensualEstimado: BigDecimal,
    val instituciones: List<InstitucionActivaResponse>,
)

// ---------------------------------------------------------------------
// Logs de auditoría (E8)
// ---------------------------------------------------------------------
data class LogAuditoriaResponse(
    val id: Long,
    val admin: String,
    val accion: String,
    val entidad: String?,
    val entidadId: String?,
    val detalle: String,
    val creadoEn: OffsetDateTime,
)
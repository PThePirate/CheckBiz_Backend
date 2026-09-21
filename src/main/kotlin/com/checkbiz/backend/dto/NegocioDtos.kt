package com.checkbiz.backend.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

// ---------------------------------------------------------------------
// Negocio (B3 — editor de Mini Landing Page)
// ---------------------------------------------------------------------
data class CrearNegocioRequest(
    @field:NotBlank @field:Size(min = 3, max = 120)
    val nombreComercial: String,

    val categoriaId: Int? = null,

    @field:Size(max = 280)
    val descripcionCorta: String? = null,

    @field:Size(max = 80)
    val ciudad: String? = null,

    @field:NotBlank @field:Pattern(regexp = "\\d{7,15}", message = "Solo dígitos, sin espacios ni símbolos")
    val whatsapp: String,
)

data class ActualizarNegocioRequest(
    @field:NotBlank @field:Size(min = 3, max = 120)
    val nombreComercial: String,

    val categoriaId: Int? = null,

    @field:Size(max = 280)
    val descripcionCorta: String? = null,

    @field:Size(max = 80)
    val ciudad: String? = null,

    @field:NotBlank @field:Pattern(regexp = "\\d{7,15}", message = "Solo dígitos, sin espacios ni símbolos")
    val whatsapp: String,

    val fotoPortadaUrl: String? = null,
    val logoUrl: String? = null,
    val videoPresentacionUrl: String? = null,
)

data class CategoriaResumenResponse(val id: Int, val nombre: String, val icono: String?)

// ---------------------------------------------------------------------
// Insignias y Certificaciones (B10)
// ---------------------------------------------------------------------
data class InsigniaResponse(
    val nombre: String,
    val descripcion: String?,
    val icono: String?,
    val obtenidaEn: OffsetDateTime,
)

data class NegocioResponse(
    val id: UUID,
    val nombreComercial: String,
    val slug: String,
    val descripcionCorta: String?,
    val ciudad: String?,
    val whatsapp: String,
    val fotoPortadaUrl: String?,
    val logoUrl: String?,
    val videoPresentacionUrl: String?,
    val categoria: CategoriaResumenResponse?,
    val trustScore: Short,
    val nivelFormalizacion: String,
    val estadoPublicacion: String,
    val totalCatalogo: Long,
    val insignias: List<InsigniaResponse>,
    val creadoEn: OffsetDateTime,
    val actualizadoEn: OffsetDateTime,
)

// ---------------------------------------------------------------------
// Catálogo (B4)
// ---------------------------------------------------------------------
data class CrearItemCatalogoRequest(
    @field:NotBlank @field:Size(min = 2, max = 120)
    val nombre: String,

    @field:DecimalMin(value = "0.0", inclusive = true)
    val precioReferencial: BigDecimal? = null,

    val fotoUrl: String? = null,

    // Traducción manual, self-service — solo planes con incluyeTraduccion (B9.1).
    @field:Size(max = 120)
    val nombreEn: String? = null,
)

data class ActualizarItemCatalogoRequest(
    @field:NotBlank @field:Size(min = 2, max = 120)
    val nombre: String,

    @field:DecimalMin(value = "0.0", inclusive = true)
    val precioReferencial: BigDecimal? = null,

    val fotoUrl: String? = null,
    val activo: Boolean = true,

    @field:Size(max = 120)
    val nombreEn: String? = null,
)

data class ItemCatalogoResponse(
    val id: UUID,
    val nombre: String,
    val precioReferencial: BigDecimal?,
    val fotoUrl: String?,
    val orden: Short,
    val activo: Boolean,
    val nombreEn: String?,
    val creadoEn: OffsetDateTime,
)

// ---------------------------------------------------------------------
// Reputación y Trust Score (B6)
// ---------------------------------------------------------------------
data class ReputacionResponse(
    val trustScore: Short,
    val nivelFormalizacion: String,
    val totalResenas: Long,
    val promedioResenas: Double,
    val totalSolicitudes: Long,
    val solicitudesConfirmadas: Long,
    val tasaConfirmacion: Double,
    val antiguedadDias: Long,
    val insignias: List<InsigniaResponse>,
)

data class ResenaDetalleResponse(
    val id: UUID,
    val clienteNombre: String,
    val estrellas: Short,
    val comentario: String?,
    val respuestaNegocio: String?,
    val respondidaEn: OffsetDateTime?,
    val creadoEn: OffsetDateTime,
)

data class ResponderResenaRequest(
    @field:NotBlank @field:Size(min = 3, max = 500)
    val respuesta: String,
)

// ---------------------------------------------------------------------
// Perfil público del negocio (A6 — Mini Landing Page)
// ---------------------------------------------------------------------
data class CapaVerificacionResponse(val capa: String, val cumplida: Boolean)

data class NegocioPublicoResponse(
    val nombreComercial: String,
    val slug: String,
    val descripcionCorta: String?,
    val ciudad: String?,
    val whatsapp: String,
    val fotoPortadaUrl: String?,
    val logoUrl: String?,
    val videoPresentacionUrl: String?,
    val categoria: CategoriaResumenResponse?,
    val trustScore: Short,
    val nivelFormalizacion: String,
    val capasVerificacion: List<CapaVerificacionResponse>,
    val insignias: List<InsigniaResponse>,
    val catalogo: List<ItemCatalogoResponse>,
    val totalResenas: Long,
    val promedioResenas: Double,
    val resenas: List<ResenaDetalleResponse>,
    val creadoEn: OffsetDateTime,
)

// ---------------------------------------------------------------------
// Búsqueda pública (A4/A5)
// ---------------------------------------------------------------------
data class NegocioResumenPublicoResponse(
    val nombreComercial: String,
    val slug: String,
    val descripcionCorta: String?,
    val ciudad: String?,
    val fotoPortadaUrl: String?,
    val logoUrl: String?,
    val categoria: CategoriaResumenResponse?,
    val trustScore: Short,
    val nivelFormalizacion: String,
)

// ---------------------------------------------------------------------
// QR de verificación física (B11)
// ---------------------------------------------------------------------

/**
 * El código es estable mientras exista el negocio — no cambia entre
 * consultas, así el dueño puede imprimirlo una sola vez. El frontend arma
 * la URL completa que va dentro del QR (origen + /qr/{codigo}); el backend
 * solo entrega el código para no acoplarse a un dominio fijo.
 */
data class QrResponse(
    val codigo: String,
    val escaneosTotal: Int,
    val creadoEn: OffsetDateTime,
)

/** Lo que necesita el frontend para redirigir tras registrar el escaneo. */
data class EscaneoQrResponse(
    val slug: String,
    val nombreComercial: String,
)

// ---------------------------------------------------------------------
// Ruta de Formalización + Simulador RIMPE (B8)
// ---------------------------------------------------------------------
data class RequisitoResponse(
    val requisito: String,
    val completado: Boolean,
    val completadoEn: OffsetDateTime?,
    /** Si es true, el dueño lo marca él mismo (ej. registro ante el SRI) — el sistema no puede verificarlo automáticamente. */
    val manual: Boolean,
)

data class NivelProgresoResponse(
    val nivel: String,
    val requisitos: List<RequisitoResponse>,
    val completo: Boolean,
)

data class RutaFormalizacionResponse(
    val nivelActual: String,
    val niveles: List<NivelProgresoResponse>,
)

data class MarcarRimpeRegistradoRequest(
    @field:NotNull
    val completado: Boolean,
)

data class SimulacionRimpeRequest(
    @field:NotNull @field:DecimalMin(value = "0.0", inclusive = true)
    val ingresosAnuales: BigDecimal,
)

data class SimulacionRimpeResponse(
    val categoria: String,
    val cuotaAnualEstimada: BigDecimal?,
    val requiereFacturaElectronica: Boolean,
    val mensaje: String,
    val advertencia: String,
)

// ---------------------------------------------------------------------
// Panel de Analítica (B7)
// ---------------------------------------------------------------------
data class PuntoSerieResponse(val fecha: String, val visitas: Long, val clicsWhatsapp: Long)

data class ComparativaResponse(
    val periodoActual: Long,
    val periodoAnterior: Long,
    /** null cuando el período anterior fue 0 — no hay base sobre la cual calcular un %. */
    val variacionPorcentual: Double?,
)

data class AnaliticaNegocioResponse(
    val totalVisitas: Long,
    val totalClicsWhatsapp: Long,
    val tasaConversion: Double,
    // false en plan Básico: el detalle diario y las comparativas quedan
    // detrás de incluyeAnaliticaAvanzada (B9.1) — los totales de arriba
    // siempre son reales para todos los planes.
    val avanzadaDisponible: Boolean,
    val serieDiaria: List<PuntoSerieResponse>,
    val comparativaSemanal: ComparativaResponse,
    val comparativaMensual: ComparativaResponse,
)

// ---------------------------------------------------------------------
// Suscripción / Planes (B9)
// ---------------------------------------------------------------------
data class PlanResponse(
    val nombre: String,
    val precioMensual: BigDecimal,
    val precioSemestral: BigDecimal,
    val limiteCatalogo: Short,
    val incluyeVideo: Boolean,
    val incluyeAnaliticaAvanzada: Boolean,
    val incluyeMultiusuario: Boolean,
    val incluyeTraduccion: Boolean,
    val incluyeCertificadoPdf: Boolean,
    val incluyeWhatsappBusinessApi: Boolean,
)

data class SuscripcionResponse(
    val plan: PlanResponse,
    val ciclo: String,
    val estado: String,
    val iniciaEn: OffsetDateTime,
    val venceEn: OffsetDateTime?,
    val totalCatalogoUsado: Long,
)

data class CambiarPlanRequest(
    @field:NotBlank
    val planNombre: String,

    @field:NotBlank @field:Pattern(regexp = "mensual|semestral")
    val ciclo: String,
)
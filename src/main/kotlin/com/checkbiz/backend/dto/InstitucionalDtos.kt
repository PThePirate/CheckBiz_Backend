package com.checkbiz.backend.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

// ---------------------------------------------------------------------
// Login institucional (D1) — ruta y JWT separados de usuarios y admin.
// ---------------------------------------------------------------------
data class InstitucionalLoginRequest(
    @field:NotBlank
    val correo: String,

    @field:NotBlank
    val password: String,
)

data class InstitucionalResponse(
    val id: UUID,
    val nombreInstitucion: String,
    val tipo: String,
    val correo: String,
)

data class InstitucionalAuthResponse(
    val mensaje: String,
    val institucion: InstitucionalResponse,
    val token: String,
)

// ---------------------------------------------------------------------
// Dashboard agregado de formalización (D2) — nunca expone datos de un
// negocio o usuario individual, solo conteos.
// ---------------------------------------------------------------------
data class NivelAgregadoResponse(val nivel: String, val totalNegocios: Long)

data class CategoriaAgregadaResponse(val categoria: String, val totalNegocios: Long)

data class DashboardB2GResponse(
    val totalNegociosPublicados: Long,
    val porNivel: List<NivelAgregadoResponse>,
    val porCategoria: List<CategoriaAgregadaResponse>,
    val proyeccionRecaudacionAnualEstimada: BigDecimal,
    val advertenciaProyeccion: String,
)

// ---------------------------------------------------------------------
// Detalle institucional (D3) — igual de agregado que D2, con una vista
// adicional por ciudad. Las ciudades con muy pocos negocios se agrupan en
// "Otras" antes de salir del backend (ver InstitucionalService), para que
// un conteo de 1 o 2 nunca pueda funcionar como identificador indirecto
// de un negocio concreto.
// ---------------------------------------------------------------------
data class CiudadAgregadaResponse(val ciudad: String, val totalNegocios: Long)

data class DashboardDetalleB2GResponse(
    val porCiudad: List<CiudadAgregadaResponse>,
    val umbralAnonimato: Long,
    val notaAnonimato: String,
)

// ---------------------------------------------------------------------
// Verificación de alumni (C — Panel B2B Universidades)
// ---------------------------------------------------------------------
data class UniversidadResponse(val id: Int, val nombre: String)

data class SolicitarVerificacionAlumniRequest(
    @field:NotNull
    val universidadId: Int,
)

data class VerificacionAlumniResponse(
    val id: UUID,
    val universidad: UniversidadResponse,
    val estado: String,
    val creadoEn: OffsetDateTime,
    val verificadoEn: OffsetDateTime?,
)

// ---------------------------------------------------------------------
// Dashboard CACES (C2) — agregado por universidad, igual de conservador
// que D2: solo conteos sobre sus propios alumni ya verificados por ella.
// ---------------------------------------------------------------------
data class DashboardCacesResponse(
    val nombreUniversidad: String,
    val totalAlumniVerificados: Long,
    val totalConNegocioPublicado: Long,
    val porNivel: List<NivelAgregadoResponse>,
    val trustScorePromedio: Double,
    val proyeccionRecaudacionAnualEstimada: BigDecimal,
    val advertenciaProyeccion: String,
)

// ---------------------------------------------------------------------
// Seguimiento de alumni (C3) — la única vista institucional que sí
// muestra un nombre propio: una universidad necesita saber A QUIÉN está
// verificando como su egresado. Nunca cédula, teléfono ni correo.
// ---------------------------------------------------------------------
data class AlumniSeguimientoResponse(
    val id: UUID,
    val nombreAlumni: String,
    val estado: String,
    val creadoEn: OffsetDateTime,
    val verificadoEn: OffsetDateTime?,
)

data class DecidirAlumniRequest(
    @field:Pattern(regexp = "verificado|rechazado")
    val estado: String,
)

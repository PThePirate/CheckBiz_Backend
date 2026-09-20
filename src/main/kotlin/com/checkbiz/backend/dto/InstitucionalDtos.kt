package com.checkbiz.backend.dto

import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
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

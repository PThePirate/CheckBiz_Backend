package com.checkbiz.backend.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.UUID

data class AdminLoginRequest(
    @field:NotBlank @field:Email
    val correo: String,

    @field:NotBlank
    val password: String,
)

data class AdminResponse(val id: UUID, val nombre: String, val correo: String, val rol: String)

data class AdminAuthResponse(val mensaje: String, val admin: AdminResponse, val token: String)

data class DecisionFotoRequest(
    @field:Pattern(regexp = "aprobada|rechazada")
    val estado: String,
    val motivoRechazo: String? = null,
)

data class VetoRequest(
    @field:NotBlank @field:Pattern(regexp = "\\d{10}")
    val cedula: String,

    @field:NotBlank @field:Size(min = 5, max = 300)
    val motivo: String,
)

data class UsuarioResumenResponse(
    val id: UUID,
    val nombreCompleto: String,
    val cedula: String,
    val correo: String,
    val creadoEn: OffsetDateTime,
)

data class VerificacionFotoResponse(
    val id: UUID,
    val usuario: UsuarioResumenResponse,
    val fotoUrl: String,
    val estado: String,
    val motivoRechazo: String?,
    val creadoEn: OffsetDateTime,
)

data class ColaFotosResponse(val estado: String, val total: Int, val items: List<VerificacionFotoResponse>)

package com.checkbiz.backend.dto

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.UUID

data class RegistroRequest(
    @field:NotBlank @field:Size(min = 3, max = 80, message = "Debe tener entre 3 y 80 caracteres")
    val nombreCompleto: String,

    @field:NotBlank @field:Email(message = "Escribe un correo válido, con @")
    @field:Size(max = 80, message = "Hasta 80 caracteres")
    val correo: String,

    @field:NotBlank @field:Size(min = 7, max = 15)
    val telefono: String,

    @field:NotBlank @field:Pattern(regexp = "\\d{10}", message = "Debe tener 10 dígitos numéricos")
    val cedula: String,

    @field:NotBlank @field:Size(min = 8, max = 80, message = "Debe tener entre 8 y 80 caracteres")
    val password: String,

    @field:AssertTrue(message = "Debes aceptar el Contrato de Adhesión y los Términos")
    val aceptoTerminos: Boolean,

    // País del número de celular — define el prefijo internacional que se
    // combina con 'telefono' para guardar el número completo en formato
    // E.164 (sin el símbolo +), el mismo que exige un envío real de SMS.
    @field:Pattern(regexp = "EC|CO|MX|US", message = "País no soportado")
    val pais: String = "EC",
)

data class LoginRequest(
    @field:NotBlank @field:Email
    val correo: String,

    @field:NotBlank
    val password: String,
)

data class OtpEnviarRequest(
    val canal: String = "email",
)

data class OtpVerificarRequest(
    @field:NotBlank @field:Pattern(regexp = "\\d{6}")
    val codigo: String,
)

data class UsuarioResponse(
    val id: UUID,
    val cedula: String,
    val nombreCompleto: String,
    val correo: String,
    val telefono: String,
    val rolCliente: Boolean,
    val rolEmprendedor: Boolean,
    val kycLayer: Short,
    val fotoVerificacionEstado: String,
    val senescytSriEstado: String,
    val estadoCedula: String,
    val fotoPerfilUrl: String?,
    val creadoEn: OffsetDateTime,
)

data class OtpInfo(val codigoDev: String?)

data class AuthResponse(
    val mensaje: String,
    val usuario: UsuarioResponse,
    val token: String,
    val otp: OtpInfo? = null,
)

data class FotoSubidaResponse(
    val id: UUID,
    val fotoUrl: String,
    val estado: String,
    val creadoEn: OffsetDateTime,
)

// ---------------------------------------------------------------------
// Perfil del comprador (A9)
// ---------------------------------------------------------------------
data class ActualizarPerfilRequest(
    @field:NotBlank @field:Size(min = 3, max = 80, message = "Debe tener entre 3 y 80 caracteres")
    val nombreCompleto: String,

    @field:NotBlank @field:Size(min = 7, max = 15)
    val telefono: String,
)

// ---------------------------------------------------------------------
// Notificaciones (A10)
// ---------------------------------------------------------------------
data class NotificacionResponse(
    val id: UUID,
    val tipo: String,
    val titulo: String,
    val mensaje: String?,
    val leida: Boolean,
    val creadoEn: OffsetDateTime,
)

data class NotificacionesResponse(
    val total: Int,
    val noLeidas: Long,
    val items: List<NotificacionResponse>,
)
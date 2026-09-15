package com.checkbiz.backend.dto

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.UUID

data class RegistroRequest(
    @field:NotBlank @field:Size(min = 3, max = 150)
    val nombreCompleto: String,

    @field:NotBlank @field:Email
    val correo: String,

    @field:NotBlank @field:Size(min = 7, max = 15)
    val telefono: String,

    @field:NotBlank @field:Pattern(regexp = "\\d{10}", message = "Debe tener 10 dígitos numéricos")
    val cedula: String,

    @field:NotBlank @field:Size(min = 8, max = 100)
    val password: String,

    @field:AssertTrue(message = "Debes aceptar el Contrato de Adhesión y los Términos")
    val aceptoTerminos: Boolean,
)

data class LoginRequest(
    @field:NotBlank @field:Email
    val correo: String,

    @field:NotBlank
    val password: String,
)

data class OtpEnviarRequest(
    val canal: String = "sms",
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

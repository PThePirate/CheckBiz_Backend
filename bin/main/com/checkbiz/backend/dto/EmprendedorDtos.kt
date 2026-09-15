package com.checkbiz.backend.dto

import jakarta.validation.constraints.AssertTrue

data class ActivarEmprendedorRequest(
    @field:AssertTrue(message = "Debes firmar el Contrato de Adhesión de Emprendedor")
    val aceptoContratoEmprendedor: Boolean,
)

data class ActivarEmprendedorResponse(
    val mensaje: String,
    val usuario: UsuarioResponse,
    val token: String,
)

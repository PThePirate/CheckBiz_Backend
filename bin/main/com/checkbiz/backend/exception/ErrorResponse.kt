package com.checkbiz.backend.exception

data class ErrorResponse(
    val error: String,
    val mensaje: String,
    val detalles: Any? = null,
)

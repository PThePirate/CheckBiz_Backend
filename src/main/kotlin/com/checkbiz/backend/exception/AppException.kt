package com.checkbiz.backend.exception

import org.springframework.http.HttpStatus

/**
 * Error de negocio con status HTTP explícito y un código estable (para que
 * el frontend pueda distinguir casos sin parsear el mensaje humano).
 */
class AppException(
    val status: HttpStatus,
    val codigo: String,
    mensaje: String,
    val detalles: Any? = null,
) : RuntimeException(mensaje)

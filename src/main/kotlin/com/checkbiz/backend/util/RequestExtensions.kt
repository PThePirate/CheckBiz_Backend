package com.checkbiz.backend.util

import jakarta.servlet.http.HttpServletRequest

/**
 * Confía en X-Forwarded-For solo si hay un proxy de confianza delante
 * (Nginx/Render/etc). En local, request.remoteAddr ya es correcto.
 */
fun HttpServletRequest.ipReal(): String {
    val forwarded = getHeader("X-Forwarded-For")
    return if (!forwarded.isNullOrBlank()) forwarded.split(",")[0].trim() else remoteAddr
}

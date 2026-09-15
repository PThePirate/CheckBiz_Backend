package com.checkbiz.backend.exception

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(AppException::class)
    fun handleAppException(ex: AppException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(ex.status).body(
            ErrorResponse(error = ex.codigo, mensaje = ex.message ?: "Error", detalles = ex.detalles)
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidacion(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val detalles = ex.bindingResult.fieldErrors.map {
            mapOf("campo" to it.field, "mensaje" to (it.defaultMessage ?: "inválido"))
        }
        return ResponseEntity.badRequest().body(
            ErrorResponse(error = "VALIDACION", mensaje = "Datos de entrada inválidos", detalles = detalles)
        )
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleIntegridad(ex: DataIntegrityViolationException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(error = "DUPLICADO", mensaje = "Ya existe un registro con esos datos")
        )

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuth(ex: AuthenticationException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ErrorResponse(error = "NO_AUTENTICADO", mensaje = "Credenciales inválidas o token expirado")
        )

    @ExceptionHandler(Exception::class)
    fun handleGenerico(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Error no controlado", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(error = "ERROR_INTERNO", mensaje = "Algo salió mal")
        )
    }
}

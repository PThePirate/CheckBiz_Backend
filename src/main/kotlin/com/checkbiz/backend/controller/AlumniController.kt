package com.checkbiz.backend.controller

import com.checkbiz.backend.config.CheckBizAuthenticationToken
import com.checkbiz.backend.config.UsuarioClaims
import com.checkbiz.backend.dto.SolicitarVerificacionAlumniRequest
import com.checkbiz.backend.dto.UniversidadResponse
import com.checkbiz.backend.dto.VerificacionAlumniResponse
import com.checkbiz.backend.service.AlumniService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
class AlumniController(private val alumniService: AlumniService) {

    private fun usuarioActual(): UsuarioClaims {
        val auth = SecurityContextHolder.getContext().authentication as CheckBizAuthenticationToken
        return auth.principal as UsuarioClaims
    }

    // Catálogo público — igual que /negocios/categorias-disponibles, para
    // llenar un <select> sin exigir sesión.
    @GetMapping("/api/universidades")
    fun listarUniversidades(): List<UniversidadResponse> = alumniService.listarUniversidades()

    @PostMapping("/api/alumni/verificacion")
    @ResponseStatus(HttpStatus.CREATED)
    fun solicitarVerificacion(@Valid @RequestBody req: SolicitarVerificacionAlumniRequest): VerificacionAlumniResponse =
        alumniService.solicitarVerificacion(usuarioActual().sub, req)

    @GetMapping("/api/alumni/mis-verificaciones")
    fun misVerificaciones(): List<VerificacionAlumniResponse> = alumniService.misVerificaciones(usuarioActual().sub)
}

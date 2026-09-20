package com.checkbiz.backend.controller

import com.checkbiz.backend.config.CheckBizAuthenticationToken
import com.checkbiz.backend.config.InstitucionalClaims
import com.checkbiz.backend.dto.AlumniSeguimientoResponse
import com.checkbiz.backend.dto.DashboardB2GResponse
import com.checkbiz.backend.dto.DashboardCacesResponse
import com.checkbiz.backend.dto.DecidirAlumniRequest
import com.checkbiz.backend.dto.InstitucionalAuthResponse
import com.checkbiz.backend.dto.InstitucionalLoginRequest
import com.checkbiz.backend.service.InstitucionalService
import jakarta.validation.Valid
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * Ruta y JWT completamente separados de /api/auth y /api/admin — una cuenta
 * institucional (universidad, Cámara de Impuestos, Cámara de Negocio) nunca
 * debe poder autenticarse como usuario o admin, ni viceversa.
 */
@RestController
@RequestMapping("/api/institucional")
class InstitucionalController(private val institucionalService: InstitucionalService) {

    private fun institucionActual(): InstitucionalClaims {
        val auth = SecurityContextHolder.getContext().authentication as CheckBizAuthenticationToken
        return auth.principal as InstitucionalClaims
    }

    // D1
    @PostMapping("/login")
    fun login(@Valid @RequestBody req: InstitucionalLoginRequest): InstitucionalAuthResponse =
        institucionalService.login(req)

    // D2 — agregado, sin datos sensibles individuales.
    @GetMapping("/dashboard-b2g")
    fun dashboardB2G(): DashboardB2GResponse = institucionalService.dashboardB2G()

    // --- Panel B2B Universidades (C) ---
    @GetMapping("/dashboard-caces")
    fun dashboardCaces(): DashboardCacesResponse = institucionalService.dashboardCaces(institucionActual().sub)

    @GetMapping("/alumni")
    fun listarAlumni(): List<AlumniSeguimientoResponse> = institucionalService.listarAlumni(institucionActual().sub)

    @PatchMapping("/alumni/{id}")
    fun decidirAlumni(@PathVariable id: UUID, @Valid @RequestBody req: DecidirAlumniRequest): AlumniSeguimientoResponse =
        institucionalService.decidirAlumni(institucionActual().sub, id, req.estado)
}

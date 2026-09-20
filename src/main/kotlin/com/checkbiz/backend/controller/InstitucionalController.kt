package com.checkbiz.backend.controller

import com.checkbiz.backend.dto.DashboardB2GResponse
import com.checkbiz.backend.dto.InstitucionalAuthResponse
import com.checkbiz.backend.dto.InstitucionalLoginRequest
import com.checkbiz.backend.service.InstitucionalService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Ruta y JWT completamente separados de /api/auth y /api/admin — una cuenta
 * institucional (universidad, Cámara de Impuestos, Cámara de Negocio) nunca
 * debe poder autenticarse como usuario o admin, ni viceversa.
 */
@RestController
@RequestMapping("/api/institucional")
class InstitucionalController(private val institucionalService: InstitucionalService) {

    // D1
    @PostMapping("/login")
    fun login(@Valid @RequestBody req: InstitucionalLoginRequest): InstitucionalAuthResponse =
        institucionalService.login(req)

    // D2 — agregado, sin datos sensibles individuales.
    @GetMapping("/dashboard-b2g")
    fun dashboardB2G(): DashboardB2GResponse = institucionalService.dashboardB2G()
}

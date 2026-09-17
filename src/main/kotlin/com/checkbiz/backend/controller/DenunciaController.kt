package com.checkbiz.backend.controller

import com.checkbiz.backend.config.CheckBizAuthenticationToken
import com.checkbiz.backend.config.UsuarioClaims
import com.checkbiz.backend.dto.CrearDenunciaRequest
import com.checkbiz.backend.dto.DenunciaCreadaResponse
import com.checkbiz.backend.service.DenunciaService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/denuncias")
class DenunciaController(private val denunciaService: DenunciaService) {

    private fun usuarioActual(): UsuarioClaims {
        val auth = SecurityContextHolder.getContext().authentication as CheckBizAuthenticationToken
        return auth.principal as UsuarioClaims
    }

    // A11 — reportar un negocio. Requiere sesión (nunca anónimo).
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun crear(@Valid @RequestBody req: CrearDenunciaRequest): DenunciaCreadaResponse =
        denunciaService.crear(usuarioActual().sub, req)
}
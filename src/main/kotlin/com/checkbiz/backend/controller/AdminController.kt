package com.checkbiz.backend.controller

import com.checkbiz.backend.config.AdminClaims
import com.checkbiz.backend.config.CheckBizAuthenticationToken
import com.checkbiz.backend.dto.*
import com.checkbiz.backend.service.AdminService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/admin")
class AdminController(private val adminService: AdminService) {

    private fun adminActual(): AdminClaims {
        val auth = SecurityContextHolder.getContext().authentication as CheckBizAuthenticationToken
        return auth.principal as AdminClaims
    }

    // Ruta separada del login público — nunca en el mismo formulario que usuarios.
    @PostMapping("/login")
    fun login(@Valid @RequestBody req: AdminLoginRequest): AdminAuthResponse = adminService.login(req)

    @GetMapping("/kyc/fotos")
    fun listarFotos(@RequestParam(required = false, defaultValue = "en_revision") estado: String): ColaFotosResponse =
        adminService.listarFotosPendientes(estado)

    @PatchMapping("/kyc/fotos/{id}")
    fun decidirFoto(
        @PathVariable id: UUID,
        @Valid @RequestBody req: DecisionFotoRequest,
    ): VerificacionFotoResponse = adminService.decidirFoto(id, adminActual().sub, req)

    @PostMapping("/veto")
    @ResponseStatus(HttpStatus.CREATED)
    fun vetar(@Valid @RequestBody req: VetoRequest): Map<String, Any> {
        val veto = adminService.vetarCedula(adminActual().sub, req)
        return mapOf("mensaje" to "Cédula vetada permanentemente", "veto" to veto)
    }
}

package com.checkbiz.backend.controller

import com.checkbiz.backend.config.CheckBizAuthenticationToken
import com.checkbiz.backend.config.UsuarioClaims
import com.checkbiz.backend.dto.ActivarEmprendedorRequest
import com.checkbiz.backend.dto.ActivarEmprendedorResponse
import com.checkbiz.backend.service.EmprendedorService
import com.checkbiz.backend.util.ipReal
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/emprendedor")
class EmprendedorController(private val emprendedorService: EmprendedorService) {

    private fun usuarioActual(): UsuarioClaims {
        val auth = SecurityContextHolder.getContext().authentication as CheckBizAuthenticationToken
        return auth.principal as UsuarioClaims
    }

    @PostMapping("/activar")
    fun activar(
        @Valid @RequestBody req: ActivarEmprendedorRequest,
        request: HttpServletRequest,
    ): ActivarEmprendedorResponse =
        emprendedorService.activar(usuarioActual().sub, request.ipReal(), request.getHeader("User-Agent"))
}

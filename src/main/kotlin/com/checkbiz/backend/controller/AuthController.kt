package com.checkbiz.backend.controller

import com.checkbiz.backend.config.CheckBizAuthenticationToken
import com.checkbiz.backend.config.UsuarioClaims
import com.checkbiz.backend.dto.*
import com.checkbiz.backend.service.ArchivoService
import com.checkbiz.backend.service.AuthService
import com.checkbiz.backend.util.ipReal
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val archivoService: ArchivoService,
) {

    /** Lee los claims del usuario autenticado desde el SecurityContext (ya validado por el filtro JWT). */
    private fun usuarioActual(): UsuarioClaims {
        val auth = SecurityContextHolder.getContext().authentication as CheckBizAuthenticationToken
        return auth.principal as UsuarioClaims
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    fun registro(@Valid @RequestBody req: RegistroRequest, request: HttpServletRequest): AuthResponse =
        authService.registrar(req, request.ipReal(), request.getHeader("User-Agent"))

    @PostMapping("/login")
    fun login(@Valid @RequestBody req: LoginRequest): AuthResponse = authService.login(req)

    @PostMapping("/otp/enviar")
    fun otpEnviar(@RequestBody(required = false) req: OtpEnviarRequest?): Map<String, Any?> {
        val canal = req?.canal ?: "sms"
        val resultado = authService.enviarOtp(usuarioActual().sub, canal)
        return mapOf(
            "mensaje" to "Código enviado por $canal",
            "expiraEn" to resultado.expiraEn,
            "otp" to resultado.codigoDev?.let { OtpInfo(it) },
        )
    }

    @PostMapping("/otp/verificar")
    fun otpVerificar(@Valid @RequestBody req: OtpVerificarRequest): AuthResponse =
        authService.verificarOtp(usuarioActual().sub, req.codigo)

    @PostMapping("/foto", consumes = ["multipart/form-data"])
    @ResponseStatus(HttpStatus.CREATED)
    fun subirFoto(@RequestParam("foto") foto: MultipartFile): Map<String, Any> {
        val fotoUrl = archivoService.guardarFotoVerificacion(foto)
        val verificacion = authService.subirFoto(usuarioActual().sub, fotoUrl)
        return mapOf(
            "mensaje" to "Foto recibida. Tu identidad está en revisión — te avisaremos cuando se apruebe.",
            "verificacion" to FotoSubidaResponse(
                id = verificacion.id!!,
                fotoUrl = verificacion.fotoUrl,
                estado = verificacion.estado,
                creadoEn = verificacion.creadoEn,
            ),
        )
    }

    @GetMapping("/me")
    fun perfil(): Map<String, UsuarioResponse> = mapOf("usuario" to authService.perfil(usuarioActual().sub))
}

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
import java.util.UUID

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
        val canal = req?.canal ?: "email"
        val resultado = authService.enviarOtp(usuarioActual().sub, canal)
        // Si codigoDev viene presente, estamos en modo desarrollo — no se
        // envio ningun correo real, y el mensaje debe dejarlo claro (antes
        // decia siempre "enviado", incluso sin enviar nada).
        val mensaje = if (resultado.codigoDev != null)
            "Código de prueba generado. No se ha enviado un correo."
        else
            "Código enviado a tu correo"
        return mapOf(
            "mensaje" to mensaje,
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

    @PutMapping("/perfil")
    fun actualizarPerfil(@Valid @RequestBody req: ActualizarPerfilRequest): Map<String, UsuarioResponse> =
        mapOf("usuario" to authService.actualizarPerfil(usuarioActual().sub, req))

    // --- Notificaciones (A10) ---
    @GetMapping("/notificaciones")
    fun listarNotificaciones(): NotificacionesResponse = authService.listarNotificaciones(usuarioActual().sub)

    @PatchMapping("/notificaciones/{id}/leida")
    fun marcarLeida(@PathVariable id: UUID): NotificacionResponse =
        authService.marcarLeida(usuarioActual().sub, id)

    @PatchMapping("/notificaciones/leidas-todas")
    fun marcarTodasLeidas() {
        authService.marcarTodasLeidas(usuarioActual().sub)
    }
}
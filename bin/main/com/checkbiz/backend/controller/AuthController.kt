package com.checkbiz.backend.controller

import com.checkbiz.backend.config.CheckBizAuthenticationToken
import com.checkbiz.backend.config.UsuarioClaims
import com.checkbiz.backend.dto.*
import com.checkbiz.backend.exception.AppException
import com.checkbiz.backend.service.ArchivoService
import com.checkbiz.backend.service.AuthService
import com.checkbiz.backend.util.ipReal
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
    fun subirFoto(
        @RequestParam("frente") frente: MultipartFile,
        @RequestParam("reverso") reverso: MultipartFile,
    ): Map<String, Any> {
        // Valida las dos imágenes antes de escribir cualquiera en disco.
        archivoService.validarImagen(frente)
        archivoService.validarImagen(reverso)
        val frenteUrl = archivoService.guardarImagen(frente)
        val reversoUrl = try { archivoService.guardarImagen(reverso) }
            catch (ex: Exception) { archivoService.eliminarImagenPrivada(frenteUrl); throw ex }
        val verificacion = try { authService.subirFoto(usuarioActual().sub, frenteUrl, reversoUrl) }
            catch (ex: Exception) {
                archivoService.eliminarImagenPrivada(frenteUrl)
                archivoService.eliminarImagenPrivada(reversoUrl)
                throw ex
            }
        return mapOf(
            "mensaje" to "Las dos caras de tu cédula se recibieron. La revisión documental está pendiente.",
            "verificacion" to FotoSubidaResponse(
                id = verificacion.id!!,
                fotoUrl = "",
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

    // --- Cuenta / Seguridad (B12) ---
    @PatchMapping("/password")
    fun cambiarPassword(@Valid @RequestBody req: CambiarPasswordRequest) {
        authService.cambiarPassword(usuarioActual().sub, req)
    }

    @PostMapping("/password/recuperar")
    fun recuperarPassword(@Valid @RequestBody req: RecuperarPasswordRequest): Map<String, Any?> {
        val resultado = authService.recuperarPassword(req.correo)
        return mapOf(
            "mensaje" to "Si el correo existe en CheckBiz, te enviamos un código de recuperación.",
            "otp" to resultado?.codigoDev?.let { OtpInfo(it) },
        )
    }

    @PostMapping("/password/restablecer")
    fun restablecerPassword(@Valid @RequestBody req: RestablecerPasswordRequest) {
        authService.restablecerPassword(req)
    }

    @DeleteMapping("/cuenta")
    fun eliminarCuenta(@Valid @RequestBody req: EliminarCuentaRequest) {
        authService.eliminarCuenta(usuarioActual().sub, req)
    }

    // --- Foto de perfil (A9) ---
    @PostMapping("/perfil/foto", consumes = ["multipart/form-data"])
    fun subirFotoPerfil(@RequestParam("foto") foto: MultipartFile): Map<String, UsuarioResponse> {
        val fotoUrl = archivoService.guardarImagen(foto)
        return mapOf("usuario" to authService.actualizarFotoPerfil(usuarioActual().sub, fotoUrl))
    }

    @GetMapping("/perfil/foto/archivo")
    fun obtenerFotoPerfil(): ResponseEntity<ByteArray> {
        val fotoUrl = authService.perfil(usuarioActual().sub).fotoPerfilUrl
            ?: throw AppException(HttpStatus.NOT_FOUND, "SIN_FOTO_PERFIL", "Todavía no has subido una foto de perfil")
        val (bytes, tipo) = archivoService.leerImagen(fotoUrl)
        return ResponseEntity.ok().contentType(tipo).body(bytes)
    }

    @PostMapping("/perfil/banner", consumes = ["multipart/form-data"])
    fun subirBannerPerfil(@RequestParam("foto") foto: MultipartFile): Map<String, UsuarioResponse> {
        val bannerUrl = archivoService.guardarImagen(foto)
        return mapOf("usuario" to authService.actualizarBannerPerfil(usuarioActual().sub, bannerUrl))
    }

    @GetMapping("/perfil/banner/archivo")
    fun obtenerBannerPerfil(): ResponseEntity<ByteArray> {
        val bannerUrl = authService.perfil(usuarioActual().sub).bannerPerfilUrl
            ?: throw AppException(HttpStatus.NOT_FOUND, "SIN_BANNER", "Todavía no has subido un banner")
        val (bytes, tipo) = archivoService.leerImagen(bannerUrl)
        return ResponseEntity.ok().contentType(tipo).body(bytes)
    }

    @GetMapping("/perfil/fotos-recientes")
    fun fotosPerfilRecientes(): List<FotoPerfilRecienteResponse> =
        authService.fotosPerfilRecientes(usuarioActual().sub)

    @GetMapping("/perfil/fotos-recientes/{id}/archivo")
    fun fotoPerfilReciente(@PathVariable id: UUID): ResponseEntity<ByteArray> {
        val fotoUrl = authService.fotoPerfilReciente(usuarioActual().sub, id)
        val (bytes, tipo) = archivoService.leerImagen(fotoUrl)
        return ResponseEntity.ok().contentType(tipo).body(bytes)
    }

    @PutMapping("/perfil/fotos-recientes/{id}/elegir")
    fun elegirFotoPerfilReciente(@PathVariable id: UUID): Map<String, UsuarioResponse> =
        mapOf("usuario" to authService.elegirFotoPerfilReciente(usuarioActual().sub, id))

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

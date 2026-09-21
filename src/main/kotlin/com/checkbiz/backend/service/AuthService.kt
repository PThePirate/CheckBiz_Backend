package com.checkbiz.backend.service

import com.checkbiz.backend.config.JwtService
import com.checkbiz.backend.config.UsuarioClaims
import com.checkbiz.backend.domain.ContratoAdhesion
import com.checkbiz.backend.domain.Notificacion
import com.checkbiz.backend.domain.Usuario
import com.checkbiz.backend.domain.VerificacionFoto
import com.checkbiz.backend.dto.*
import com.checkbiz.backend.exception.AppException
import com.checkbiz.backend.repository.ContratoAdhesionRepository
import com.checkbiz.backend.repository.NotificacionRepository
import com.checkbiz.backend.repository.UsuarioRepository
import com.checkbiz.backend.repository.VerificacionFotoRepository
import com.checkbiz.backend.repository.VetoCedulaRepository
import com.checkbiz.backend.util.Modulo10
import com.checkbiz.backend.util.Telefonos
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
class AuthService(
    private val usuarioRepository: UsuarioRepository,
    private val vetoRepository: VetoCedulaRepository,
    private val contratoRepository: ContratoAdhesionRepository,
    private val verificacionFotoRepository: VerificacionFotoRepository,
    private val notificacionRepository: NotificacionRepository,
    private val otpService: OtpService,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder,
) {

    /** Paso 1 del registro: datos + Capa 1 (Módulo 10). Crea la cuenta base. */
    @Transactional
    fun registrar(req: RegistroRequest, ip: String, userAgent: String?): AuthResponse {
        // 1) Estructura matemática de la cédula (Capa 1)
        val validacion = Modulo10.validar(req.cedula)
        if (!validacion.valida) {
            throw AppException(HttpStatus.BAD_REQUEST, "CEDULA_INVALIDA", validacion.motivo ?: "Cédula inválida")
        }

        // 2) Veto (blacklist del admin) — se revisa ANTES de crear la cuenta
        if (vetoRepository.existsByCedula(req.cedula)) {
            throw AppException(
                HttpStatus.FORBIDDEN,
                "CEDULA_VETADA",
                "Esta cédula no puede registrarse en CheckBiz. Si crees que es un error, contáctanos."
            )
        }

        // 3) Unicidad (1 cédula = 1 cuenta)
        if (usuarioRepository.existsByCedula(req.cedula)) {
            throw AppException(HttpStatus.CONFLICT, "YA_REGISTRADO", "Ya existe una cuenta con esa cédula")
        }
        if (usuarioRepository.existsByCorreo(req.correo)) {
            throw AppException(HttpStatus.CONFLICT, "YA_REGISTRADO", "Ya existe una cuenta con ese correo")
        }

        val usuario = usuarioRepository.save(
            Usuario(
                cedula = req.cedula,
                nombreCompleto = req.nombreCompleto,
                correo = req.correo,
                telefono = Telefonos.combinar(req.pais, req.telefono),
                passwordHash = passwordEncoder.encode(req.password),
                aceptoTerminos = true,
                kycLayer = 1,
            )
        )

        // Registro legal del Contrato de Adhesión: IP + fecha/hora exactas.
        contratoRepository.save(
            ContratoAdhesion(
                usuario = usuario,
                tipo = "adhesion_general",
                ipFirma = ip,
                userAgent = userAgent,
            )
        )

        val envio = otpService.enviar(usuario, "sms")

        return AuthResponse(
            mensaje = "Cuenta creada. Verifica tu teléfono para continuar (Capa 2).",
            usuario = usuario.aDto(),
            token = jwtService.generarTokenUsuario(usuario.aClaims()),
            otp = envio.codigoDev?.let { OtpInfo(it) },
        )
    }

    fun login(req: LoginRequest): AuthResponse {
        val usuario = usuarioRepository.findByCorreo(req.correo).orElseThrow { credencialesInvalidas() }

        if (!passwordEncoder.matches(req.password, usuario.passwordHash)) {
            throw credencialesInvalidas()
        }

        // A diferencia de las credenciales, el veto SÍ se comunica explícitamente:
        // no es un tema de seguridad ocultable, es una cuenta suspendida.
        if (usuario.estadoCedula == "vetada") {
            throw AppException(
                HttpStatus.FORBIDDEN,
                "CUENTA_VETADA",
                "Tu cuenta fue suspendida. Contacta a soporte si crees que es un error."
            )
        }

        return AuthResponse(
            mensaje = "Sesión iniciada",
            usuario = usuario.aDto(),
            token = jwtService.generarTokenUsuario(usuario.aClaims()),
        )
    }

    fun enviarOtp(usuarioId: UUID, canal: String): OtpService.EnvioResultado {
        val usuario = usuarioRepository.findById(usuarioId).orElseThrow()
        return otpService.enviar(usuario, canal)
    }

    @Transactional
    fun verificarOtp(usuarioId: UUID, codigo: String): AuthResponse {
        otpService.verificar(usuarioId, codigo)
        val usuario = usuarioRepository.findById(usuarioId).orElseThrow()
        return AuthResponse(
            mensaje = "Identidad verificada — Capa 2 de 5",
            usuario = usuario.aDto(),
            token = jwtService.generarTokenUsuario(usuario.aClaims()),
        )
    }

    @Transactional
    fun subirFoto(usuarioId: UUID, fotoUrl: String): VerificacionFoto {
        val usuario = usuarioRepository.findById(usuarioId).orElseThrow()

        val verificacion = verificacionFotoRepository.save(
            VerificacionFoto(usuario = usuario, fotoUrl = fotoUrl, estado = "en_revision")
        )

        usuario.fotoVerificacionEstado = "en_revision"
        usuario.actualizadoEn = OffsetDateTime.now()
        usuarioRepository.save(usuario)

        return verificacion
    }

    fun perfil(usuarioId: UUID): UsuarioResponse =
        usuarioRepository.findById(usuarioId).orElseThrow().aDto()

    @Transactional
    fun actualizarPerfil(usuarioId: UUID, req: ActualizarPerfilRequest): UsuarioResponse {
        val usuario = usuarioRepository.findById(usuarioId).orElseThrow()
        usuario.nombreCompleto = req.nombreCompleto
        usuario.telefono = req.telefono
        usuario.actualizadoEn = OffsetDateTime.now()
        usuarioRepository.save(usuario)
        return usuario.aDto()
    }

    // ===================================================================
    // Cuenta / Seguridad (B12)
    // ===================================================================
    @Transactional
    fun cambiarPassword(usuarioId: UUID, req: CambiarPasswordRequest) {
        val usuario = usuarioRepository.findById(usuarioId).orElseThrow()
        if (!passwordEncoder.matches(req.passwordActual, usuario.passwordHash)) {
            throw AppException(HttpStatus.BAD_REQUEST, "PASSWORD_INCORRECTA", "Tu contraseña actual no es correcta")
        }
        usuario.passwordHash = passwordEncoder.encode(req.passwordNueva)
        usuario.actualizadoEn = OffsetDateTime.now()
        usuarioRepository.save(usuario)
    }

    /** Nunca revela si el correo existe — misma respuesta genérica en ambos casos (evita enumeración de cuentas). */
    @Transactional
    fun recuperarPassword(correo: String): OtpService.EnvioResultado? {
        val usuario = usuarioRepository.findByCorreo(correo).orElse(null) ?: return null
        if (usuario.estadoCedula != "activa") return null
        return otpService.enviarRecuperacion(usuario)
    }

    @Transactional
    fun restablecerPassword(req: RestablecerPasswordRequest) {
        val usuario = usuarioRepository.findByCorreo(req.correo).orElseThrow {
            AppException(HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA", "Código o correo inválido")
        }
        otpService.verificarRecuperacion(usuario.id!!, req.codigo)
        usuario.passwordHash = passwordEncoder.encode(req.passwordNueva)
        usuario.actualizadoEn = OffsetDateTime.now()
        usuarioRepository.save(usuario)
    }

    /**
     * Desactiva la cuenta en vez de borrarla — un usuario puede tener
     * negocios, reseñas y solicitudes que otras personas siguen necesitando
     * ver. Reutiliza el mismo corte de sesión inmediato que el veto (E4):
     * el filtro JWT bloquea cualquier estado_cedula distinto de "activa".
     */
    @Transactional
    fun eliminarCuenta(usuarioId: UUID, req: EliminarCuentaRequest) {
        val usuario = usuarioRepository.findById(usuarioId).orElseThrow()
        if (!passwordEncoder.matches(req.password, usuario.passwordHash)) {
            throw AppException(HttpStatus.BAD_REQUEST, "PASSWORD_INCORRECTA", "Tu contraseña no es correcta")
        }
        usuario.estadoCedula = "eliminada"
        usuario.actualizadoEn = OffsetDateTime.now()
        usuarioRepository.save(usuario)
    }

    // ===================================================================
    // Foto de perfil (A9)
    // ===================================================================
    @Transactional
    fun actualizarFotoPerfil(usuarioId: UUID, fotoUrl: String): UsuarioResponse {
        val usuario = usuarioRepository.findById(usuarioId).orElseThrow()
        usuario.fotoPerfilUrl = fotoUrl
        usuario.actualizadoEn = OffsetDateTime.now()
        usuarioRepository.save(usuario)
        return usuario.aDto()
    }

    // ===================================================================
    // Notificaciones (A10)
    // ===================================================================
    fun listarNotificaciones(usuarioId: UUID): NotificacionesResponse {
        val items = notificacionRepository.findByUsuarioIdOrderByCreadoEnDesc(usuarioId)
        return NotificacionesResponse(
            total = items.size,
            noLeidas = notificacionRepository.countByUsuarioIdAndLeidaFalse(usuarioId),
            items = items.map { it.aDto() },
        )
    }

    @Transactional
    fun marcarLeida(usuarioId: UUID, notificacionId: UUID): NotificacionResponse {
        val notif = notificacionRepository.findById(notificacionId)
            .orElseThrow { AppException(HttpStatus.NOT_FOUND, "NO_ENCONTRADA", "Notificación no encontrada") }
        if (notif.usuario?.id != usuarioId) {
            throw AppException(HttpStatus.FORBIDDEN, "NO_AUTORIZADO", "Esta notificación no te pertenece")
        }
        notif.leida = true
        notificacionRepository.save(notif)
        return notif.aDto()
    }

    @Transactional
    fun marcarTodasLeidas(usuarioId: UUID) {
        notificacionRepository.findByUsuarioIdAndLeidaFalse(usuarioId).forEach {
            it.leida = true
            notificacionRepository.save(it)
        }
    }

    private fun credencialesInvalidas() =
        AppException(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", "Correo o contraseña incorrectos")
}

fun Usuario.aDto() = UsuarioResponse(
    id = id!!,
    cedula = cedula,
    nombreCompleto = nombreCompleto,
    correo = correo,
    telefono = telefono,
    rolCliente = rolCliente,
    rolEmprendedor = rolEmprendedor,
    kycLayer = kycLayer,
    fotoVerificacionEstado = fotoVerificacionEstado,
    senescytSriEstado = senescytSriEstado,
    estadoCedula = estadoCedula,
    fotoPerfilUrl = fotoPerfilUrl,
    creadoEn = creadoEn,
)

fun Usuario.aClaims() = UsuarioClaims(
    sub = id!!,
    rolCliente = rolCliente,
    rolEmprendedor = rolEmprendedor,
    kycLayer = kycLayer,
)

fun Notificacion.aDto() = NotificacionResponse(
    id = id!!, tipo = tipo, titulo = titulo, mensaje = mensaje, leida = leida, creadoEn = creadoEn,
)
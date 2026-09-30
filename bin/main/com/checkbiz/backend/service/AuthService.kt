package com.checkbiz.backend.service

import com.checkbiz.backend.config.JwtService
import com.checkbiz.backend.config.UsuarioClaims
import com.checkbiz.backend.domain.ContratoAdhesion
import com.checkbiz.backend.domain.Notificacion
import com.checkbiz.backend.domain.Usuario
import com.checkbiz.backend.domain.FotoPerfilReciente
import com.checkbiz.backend.domain.VerificacionFoto
import com.checkbiz.backend.dto.*
import com.checkbiz.backend.exception.AppException
import com.checkbiz.backend.repository.ContratoAdhesionRepository
import com.checkbiz.backend.repository.NotificacionRepository
import com.checkbiz.backend.repository.NegocioRepository
import com.checkbiz.backend.repository.UsuarioRepository
import com.checkbiz.backend.repository.FotoPerfilRecienteRepository
import com.checkbiz.backend.repository.VerificacionFotoRepository
import com.checkbiz.backend.repository.VetoCedulaRepository
import com.checkbiz.backend.util.Modulo10
import com.checkbiz.backend.util.Telefonos
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.random.Random

@Service
class AuthService(
    private val usuarioRepository: UsuarioRepository,
    private val vetoRepository: VetoCedulaRepository,
    private val contratoRepository: ContratoAdhesionRepository,
    private val verificacionFotoRepository: VerificacionFotoRepository,
    private val notificacionRepository: NotificacionRepository,
    private val fotoPerfilRecienteRepository: FotoPerfilRecienteRepository,
    private val otpService: OtpService,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder,
    private val jdbcTemplate: JdbcTemplate,
    private val negocioRepository: NegocioRepository,
    private val negocioService: NegocioService,
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
        val usuario = usuarioRepository.findByCorreo(req.correo.trim().lowercase()).orElseThrow {
            AppException(HttpStatus.NOT_FOUND, "USUARIO_NO_EXISTE", "Este usuario no existe")
        }

        if (!passwordEncoder.matches(req.password, usuario.passwordHash)) {
            throw credencialesInvalidas()
        }

        // A diferencia de las credenciales, el veto SÍ se comunica explícitamente:
        // no es un tema de seguridad ocultable, es una cuenta suspendida.
        if (usuario.estadoCedula != "activa") {
            throw AppException(
                HttpStatus.FORBIDDEN,
                if (usuario.estadoCedula == "vetada") "CUENTA_VETADA" else "CUENTA_ELIMINADA",
                if (usuario.estadoCedula == "vetada") "Tu cuenta fue suspendida. Contacta a soporte si crees que es un error." else "Esta cuenta fue eliminada."
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
    fun subirFoto(usuarioId: UUID, fotoUrl: String, fotoReversoUrl: String): VerificacionFoto {
        val usuario = usuarioRepository.findById(usuarioId).orElseThrow()

        if (usuario.kycLayer < 2) {
            throw AppException(HttpStatus.FORBIDDEN, "CORREO_PENDIENTE", "Primero confirma tu correo")
        }

        val verificacion = verificacionFotoRepository.save(
            VerificacionFoto(usuario = usuario, fotoUrl = fotoUrl, fotoReversoUrl = fotoReversoUrl, estado = "en_revision")
        )

        usuario.fotoVerificacionEstado = "en_revision"
        if (usuario.kycLayer > 2) usuario.kycLayer = 2
        usuario.actualizadoEn = OffsetDateTime.now()
        usuarioRepository.save(usuario)
        negocioRepository.findByUsuarioId(usuarioId)?.id?.let { negocioService.recalcularTrustScore(it) }

        return verificacion
    }

    fun perfil(usuarioId: UUID): UsuarioResponse =
        usuarioRepository.findById(usuarioId).orElseThrow().aDto()

    @Transactional
    fun actualizarPerfil(usuarioId: UUID, req: ActualizarPerfilRequest): UsuarioResponse {
        val usuario = usuarioRepository.findById(usuarioId).orElseThrow()
        usuario.nombreCompleto = req.nombreCompleto
        usuario.telefono = req.telefono
        if (req.nombreUsuario != null) {
            val nombre = req.nombreUsuario.trim().takeIf { it.isNotEmpty() }
            if (nombre != null && nombre.length < 3) throw AppException(HttpStatus.BAD_REQUEST, "NOMBRE_USUARIO_CORTO", "El nombre de usuario debe tener al menos 3 caracteres")
            if (nombre != null && usuarioRepository.existsByNombreUsuarioIgnoreCaseAndIdNot(nombre, usuarioId)) {
                throw AppException(HttpStatus.CONFLICT, "NOMBRE_USUARIO_OCUPADO", "Ese nombre de usuario ya está en uso")
            }
            usuario.nombreUsuario = nombre
        }
        if (req.descripcionPerfil != null) usuario.descripcionPerfil = req.descripcionPerfil.trim().takeIf { it.isNotEmpty() }
        if (req.estadoPerfil != null) usuario.estadoPerfil = req.estadoPerfil.trim().takeIf { it.isNotEmpty() }
        if (req.negociosGuardadosSlugs != null) usuario.negociosGuardadosSlugs = req.negociosGuardadosSlugs.distinct().take(5).joinToString(",")
        if (req.negocioFavoritoSlug != null) usuario.negocioFavoritoSlug = req.negocioFavoritoSlug.trim().takeIf { it.isNotEmpty() }
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
     * Borra la identidad original. Si hay historial compartido, lo reasigna a
     * una identidad técnica nueva, sin credenciales utilizables ni datos de la
     * persona; así las solicitudes, reseñas y conversaciones siguen íntegras.
     * La operación completa ocurre en una sola transacción.
     */
    @Transactional
    fun eliminarCuenta(usuarioId: UUID, req: EliminarCuentaRequest) {
        val usuario = usuarioRepository.findById(usuarioId).orElseThrow()
        if (!passwordEncoder.matches(req.password, usuario.passwordHash)) {
            throw AppException(HttpStatus.BAD_REQUEST, "PASSWORD_INCORRECTA", "Tu contraseña no es correcta")
        }

        val tieneHistorial = jdbcTemplate.queryForObject(
            """SELECT EXISTS (
                SELECT 1 FROM negocios WHERE usuario_id = ?
                UNION ALL SELECT 1 FROM solicitudes WHERE cliente_id = ?
                UNION ALL SELECT 1 FROM resenas WHERE cliente_id = ?
                UNION ALL SELECT 1 FROM mensajes_solicitud WHERE autor_id = ?
                UNION ALL SELECT 1 FROM denuncias WHERE reportante_id = ?
            )""", Boolean::class.java, usuarioId, usuarioId, usuarioId, usuarioId, usuarioId
        ) == true

        if (tieneHistorial) {
            val anonimoId = UUID.randomUUID()
            var cedulaTecnica: String
            // El prefijo 99 no corresponde a una cédula válida: nunca ocupa la de otra persona.
            do { cedulaTecnica = "99" + Random.nextLong(100_000_000L).toString().padStart(8, '0') }
            while (usuarioRepository.existsByCedula(cedulaTecnica))
            jdbcTemplate.update(
                """INSERT INTO usuarios (id, cedula, nombre_completo, correo, telefono, pais,
                    password_hash, rol_cliente, rol_emprendedor, kyc_layer, estado_cedula,
                    acepto_terminos) VALUES (?, ?, 'Usuario eliminado', ?, '0000000000', 'EC',
                    ?, false, false, 1, 'eliminada', false)""",
                anonimoId, cedulaTecnica, "eliminado-$anonimoId@anonimo.invalid",
                passwordEncoder.encode(UUID.randomUUID().toString())
            )
            jdbcTemplate.update("UPDATE denuncias SET reportante_id = ? WHERE reportante_id = ?", anonimoId, usuarioId)
            jdbcTemplate.update("UPDATE solicitudes SET cliente_id = ?, estado = CASE WHEN estado IN ('enviada', 'en_conversacion') THEN 'cancelada' ELSE estado END WHERE cliente_id = ?", anonimoId, usuarioId)
            jdbcTemplate.update("UPDATE resenas SET cliente_id = ? WHERE cliente_id = ?", anonimoId, usuarioId)
            jdbcTemplate.update("UPDATE mensajes_solicitud SET autor_id = ? WHERE autor_id = ?", anonimoId, usuarioId)
            jdbcTemplate.update("UPDATE negocios SET usuario_id = ?, estado_publicacion = 'suspendido', descripcion_corta = NULL, slogan = NULL, ciudad = NULL, whatsapp = '', foto_portada_url = NULL, logo_url = NULL, video_presentacion_url = NULL, landing_bloques = '[]' WHERE usuario_id = ?", anonimoId, usuarioId)
            jdbcTemplate.update("UPDATE catalogo_items SET foto_url = NULL WHERE negocio_id IN (SELECT id FROM negocios WHERE usuario_id = ?)", anonimoId)
            jdbcTemplate.update("UPDATE solicitudes SET estado = 'cancelada' WHERE negocio_id IN (SELECT id FROM negocios WHERE usuario_id = ?) AND estado IN ('enviada', 'en_conversacion')", anonimoId)
        }

        // Las referencias personales restantes tienen ON DELETE CASCADE:
        // sesiones, OTP, fotos KYC/perfil, notificaciones, contratos y equipo.
        usuarioRepository.delete(usuario)
        usuarioRepository.flush()
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
        fotoPerfilRecienteRepository.findByUsuarioIdOrderByCreadoEnDesc(usuarioId)
            .filter { it.fotoUrl == fotoUrl }.forEach { fotoPerfilRecienteRepository.delete(it) }
        fotoPerfilRecienteRepository.save(FotoPerfilReciente(usuario = usuario, fotoUrl = fotoUrl))
        fotoPerfilRecienteRepository.findByUsuarioIdOrderByCreadoEnDesc(usuarioId).drop(6)
            .forEach { fotoPerfilRecienteRepository.delete(it) }
        return usuario.aDto()
    }

    @Transactional
    fun actualizarBannerPerfil(usuarioId: UUID, bannerUrl: String): UsuarioResponse {
        val usuario = usuarioRepository.findById(usuarioId).orElseThrow()
        usuario.bannerPerfilUrl = bannerUrl
        usuario.actualizadoEn = OffsetDateTime.now()
        return usuarioRepository.save(usuario).aDto()
    }

    @Transactional(readOnly = true)
    fun fotosPerfilRecientes(usuarioId: UUID): List<FotoPerfilRecienteResponse> =
        fotoPerfilRecienteRepository.findByUsuarioIdOrderByCreadoEnDesc(usuarioId)
            .take(6).map { FotoPerfilRecienteResponse(it.id, it.creadoEn) }

    @Transactional(readOnly = true)
    fun fotoPerfilReciente(usuarioId: UUID, fotoId: UUID): String =
        fotoPerfilRecienteRepository.findByIdAndUsuarioId(fotoId, usuarioId)?.fotoUrl
            ?: throw AppException(HttpStatus.NOT_FOUND, "FOTO_NO_ENCONTRADA", "Esta foto no está en tu historial")

    @Transactional
    fun elegirFotoPerfilReciente(usuarioId: UUID, fotoId: UUID): UsuarioResponse =
        actualizarFotoPerfil(usuarioId, fotoPerfilReciente(usuarioId, fotoId))

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
    bannerPerfilUrl = bannerPerfilUrl,
    negocioFavoritoSlug = negocioFavoritoSlug,
    negociosGuardadosSlugs = negociosGuardadosSlugs?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
    nombreUsuario = nombreUsuario,
    descripcionPerfil = descripcionPerfil,
    estadoPerfil = estadoPerfil,
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

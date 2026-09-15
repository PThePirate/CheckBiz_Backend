package com.checkbiz.backend.service

import com.checkbiz.backend.config.JwtService
import com.checkbiz.backend.config.UsuarioClaims
import com.checkbiz.backend.domain.ContratoAdhesion
import com.checkbiz.backend.domain.Usuario
import com.checkbiz.backend.domain.VerificacionFoto
import com.checkbiz.backend.dto.*
import com.checkbiz.backend.exception.AppException
import com.checkbiz.backend.repository.ContratoAdhesionRepository
import com.checkbiz.backend.repository.UsuarioRepository
import com.checkbiz.backend.repository.VerificacionFotoRepository
import com.checkbiz.backend.repository.VetoCedulaRepository
import com.checkbiz.backend.util.Modulo10
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
                telefono = req.telefono,
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
    creadoEn = creadoEn,
)

fun Usuario.aClaims() = UsuarioClaims(
    sub = id!!,
    rolCliente = rolCliente,
    rolEmprendedor = rolEmprendedor,
    kycLayer = kycLayer,
)

package com.checkbiz.backend.service

import com.checkbiz.backend.config.AdminClaims
import com.checkbiz.backend.config.JwtService
import com.checkbiz.backend.domain.AdminLogAuditoria
import com.checkbiz.backend.domain.Notificacion
import com.checkbiz.backend.domain.VetoCedula
import com.checkbiz.backend.dto.*
import com.checkbiz.backend.exception.AppException
import com.checkbiz.backend.repository.*
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
class AdminService(
    private val adminRepository: AdminRepository,
    private val usuarioRepository: UsuarioRepository,
    private val verificacionFotoRepository: VerificacionFotoRepository,
    private val vetoRepository: VetoCedulaRepository,
    private val notificacionRepository: NotificacionRepository,
    private val logRepository: AdminLogAuditoriaRepository,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder,
) {

    fun login(req: AdminLoginRequest): AdminAuthResponse {
        val admin = adminRepository.findByCorreo(req.correo).orElseThrow { credencialesInvalidas() }

        if (!admin.activo || !passwordEncoder.matches(req.password, admin.passwordHash)) {
            throw credencialesInvalidas()
        }

        return AdminAuthResponse(
            mensaje = "Sesión de administrador iniciada",
            admin = AdminResponse(id = admin.id!!, nombre = admin.nombre, correo = admin.correo, rol = admin.rol),
            token = jwtService.generarTokenAdmin(AdminClaims(sub = admin.id!!, rol = admin.rol)),
        )
    }

    fun listarFotosPendientes(estado: String): ColaFotosResponse {
        val estadoValido = if (estado in listOf("en_revision", "aprobada", "rechazada")) estado else "en_revision"
        val items = verificacionFotoRepository.findByEstadoOrderByCreadoEnAsc(estadoValido)

        return ColaFotosResponse(
            estado = estadoValido,
            total = items.size,
            items = items.map { v ->
                val u = v.usuario!!
                VerificacionFotoResponse(
                    id = v.id!!,
                    usuario = UsuarioResumenResponse(
                        id = u.id!!, nombreCompleto = u.nombreCompleto, cedula = u.cedula,
                        correo = u.correo, creadoEn = u.creadoEn,
                    ),
                    fotoUrl = v.fotoUrl,
                    estado = v.estado,
                    motivoRechazo = v.motivoRechazo,
                    creadoEn = v.creadoEn,
                )
            }
        )
    }

    @Transactional
    fun decidirFoto(verificacionId: UUID, adminId: UUID, req: DecisionFotoRequest): VerificacionFotoResponse {
        if (req.estado == "rechazada" && req.motivoRechazo.isNullOrBlank()) {
            throw AppException(HttpStatus.BAD_REQUEST, "MOTIVO_REQUERIDO", "Debes indicar el motivo del rechazo")
        }

        val verificacion = verificacionFotoRepository.findById(verificacionId)
            .orElseThrow { AppException(HttpStatus.NOT_FOUND, "NO_ENCONTRADA", "Verificación no encontrada") }

        verificacion.estado = req.estado
        verificacion.motivoRechazo = if (req.estado == "rechazada") req.motivoRechazo else null
        verificacion.revisadoPor = adminId
        verificacion.revisadoEn = OffsetDateTime.now()
        verificacionFotoRepository.save(verificacion)

        val usuario = verificacion.usuario!!
        usuario.fotoVerificacionEstado = req.estado
        if (req.estado == "aprobada" && usuario.kycLayer < 3) {
            usuario.kycLayer = 3
        }
        usuario.actualizadoEn = OffsetDateTime.now()
        usuarioRepository.save(usuario)

        notificacionRepository.save(
            Notificacion(
                usuario = usuario,
                tipo = if (req.estado == "aprobada") "kyc_aprobado" else "kyc_rechazado",
                titulo = if (req.estado == "aprobada") "Identidad verificada" else "Verificación rechazada",
                mensaje = if (req.estado == "aprobada")
                    "Tu foto de verificación fue aprobada — Capa 3 completada."
                else
                    "Tu foto fue rechazada: ${req.motivoRechazo}. Puedes volver a intentarlo.",
            )
        )

        registrarLog(adminId, "kyc_foto_${req.estado}", "verificaciones_foto", verificacionId.toString())

        return VerificacionFotoResponse(
            id = verificacion.id!!,
            usuario = UsuarioResumenResponse(
                id = usuario.id!!, nombreCompleto = usuario.nombreCompleto, cedula = usuario.cedula,
                correo = usuario.correo, creadoEn = usuario.creadoEn,
            ),
            fotoUrl = verificacion.fotoUrl,
            estado = verificacion.estado,
            motivoRechazo = verificacion.motivoRechazo,
            creadoEn = verificacion.creadoEn,
        )
    }

    @Transactional
    fun vetarCedula(adminId: UUID, req: VetoRequest): VetoCedula {
        if (vetoRepository.existsByCedula(req.cedula)) {
            throw AppException(HttpStatus.CONFLICT, "YA_VETADA", "Esta cédula ya está en la lista de veto")
        }

        val veto = vetoRepository.save(VetoCedula(cedula = req.cedula, motivo = req.motivo, vetadoPor = adminId))

        usuarioRepository.findByCedula(req.cedula).ifPresent { usuario ->
            usuario.estadoCedula = "vetada"
            usuario.actualizadoEn = OffsetDateTime.now()
            usuarioRepository.save(usuario)
        }

        registrarLog(adminId, "veto_cedula", "usuarios", req.cedula)

        return veto
    }

    private fun registrarLog(adminId: UUID, accion: String, entidad: String, entidadId: String) {
        logRepository.save(
            AdminLogAuditoria(adminId = adminId, accion = accion, entidadAfectada = entidad, entidadId = entidadId)
        )
    }

    private fun credencialesInvalidas() =
        AppException(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", "Correo o contraseña incorrectos")
}

package com.checkbiz.backend.service

import com.checkbiz.backend.config.AdminClaims
import com.checkbiz.backend.config.JwtService
import com.checkbiz.backend.domain.AdminLogAuditoria
import com.checkbiz.backend.domain.Categoria
import com.checkbiz.backend.domain.Notificacion
import com.checkbiz.backend.domain.VetoCedula
import com.checkbiz.backend.dto.*
import com.checkbiz.backend.exception.AppException
import com.checkbiz.backend.repository.*
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
    private val negocioRepository: NegocioRepository,
    private val contratoRepository: ContratoAdhesionRepository,
    private val solicitudRepository: SolicitudRepository,
    private val resenaRepository: ResenaRepository,
    private val categoriaRepository: CategoriaRepository,
    private val denunciaRepository: DenunciaRepository,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder,
    private val archivoService: ArchivoService,
) {

    // ===================================================================
    // Login
    // ===================================================================
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

    // ===================================================================
    // KYC (E2) — sin cambios de lógica, ya estaba probado
    // ===================================================================
    // readOnly=true mantiene la sesión de Hibernate abierta mientras se
    // recorre la lista y se toca v.usuario (LAZY) — sin esto, con
    // open-in-view: false, la petición completa fallaba con
    // LazyInitializationException ("no session") apenas había una foto
    // pendiente que revisar.
    @Transactional(readOnly = true)
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

    // Sirve el archivo real de una foto de verificación (E2). Antes se
    // exponía como estático público en /uploads/**; ahora solo un admin
    // autenticado puede pedirla, y siempre por su id de verificación (no
    // por nombre de archivo adivinable).
    fun obtenerArchivoFoto(verificacionId: UUID): Pair<ByteArray, MediaType> {
        val verificacion = verificacionFotoRepository.findById(verificacionId)
            .orElseThrow { AppException(HttpStatus.NOT_FOUND, "NO_ENCONTRADA", "Verificación no encontrada") }
        return archivoService.leerImagen(verificacion.fotoUrl)
    }

    // ===================================================================
    // Veto por cédula (E4) — sin cambios de lógica, ya estaba probado
    // ===================================================================
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

    // ===================================================================
    // Panel general — estadísticas y actividad (NUEVO)
    // ===================================================================
    fun estadisticas(): EstadisticasResponse {
        val usuariosPorCapa = (1..5).associateWith { usuarioRepository.countByKycLayer(it.toShort()) }
        val aprobadas = verificacionFotoRepository.countByEstado("aprobada")
        val rechazadas = verificacionFotoRepository.countByEstado("rechazada")
        val tasa = if (aprobadas + rechazadas == 0L) 0.0 else aprobadas.toDouble() / (aprobadas + rechazadas)

        return EstadisticasResponse(
            usuariosTotales = usuarioRepository.count(),
            usuariosPorCapa = usuariosPorCapa,
            negociosPublicados = negocioRepository.countByEstadoPublicacion("publicado"),
            tasaAprobacionKyc = tasa,
            cedulasVetadas = vetoRepository.count(),
            kycPendientes = verificacionFotoRepository.countByEstado("en_revision"),
        )
    }

    fun actividadReciente(limite: Int): List<ActividadItemResponse> {
        val logs = logRepository.findAllByOrderByCreadoEnDesc(PageRequest.of(0, limite))
        return logs.map { log ->
            val (tipo, texto) = construirActividad(log)
            ActividadItemResponse(id = log.id!!, tipo = tipo, texto = texto, fecha = log.creadoEn)
        }
    }

    private fun construirActividad(log: AdminLogAuditoria): Pair<String, String> = when (log.accion) {
        "kyc_foto_aprobada" -> "kyc_aprobado" to "Aprobó la verificación de ${nombreDeVerificacion(log.entidadId)}"
        "kyc_foto_rechazada" -> "kyc_rechazado" to "Rechazó la verificación de ${nombreDeVerificacion(log.entidadId)}"
        "veto_cedula" -> "veto" to "Vetó la cédula ${log.entidadId}"
        else -> "otro" to log.accion
    }

    private fun nombreDeVerificacion(verificacionId: String?): String {
        if (verificacionId == null) return "un usuario"
        return try {
            verificacionFotoRepository.findById(UUID.fromString(verificacionId))
                .map { it.usuario?.nombreCompleto ?: "un usuario" }
                .orElse("un usuario")
        } catch (e: IllegalArgumentException) {
            "un usuario"
        }
    }

    // ===================================================================
    // Búsqueda de usuarios + ficha 360° (NUEVO)
    // ===================================================================
    fun buscarUsuarios(query: String): List<UsuarioResumenResponse> {
        if (query.isBlank()) return emptyList()
        return usuarioRepository.buscar(query.trim()).map { u ->
            UsuarioResumenResponse(id = u.id!!, nombreCompleto = u.nombreCompleto, cedula = u.cedula, correo = u.correo, creadoEn = u.creadoEn)
        }
    }

    fun obtenerUsuarioDetalle(id: UUID): UsuarioDetalleResponse {
        val u = usuarioRepository.findById(id)
            .orElseThrow { AppException(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "Usuario no encontrado") }

        val negocio = negocioRepository.findByUsuarioId(id)?.let {
            NegocioResumenResponse(it.nombreComercial, it.nivelFormalizacion, it.trustScore)
        }
        val contratos = contratoRepository.findByUsuarioIdOrderByFirmadoEnAsc(id).map {
            ContratoResumenResponse(it.tipo, it.firmadoEn, it.ipFirma)
        }

        return UsuarioDetalleResponse(
            id = u.id!!, nombreCompleto = u.nombreCompleto, cedula = u.cedula, correo = u.correo,
            telefono = u.telefono, rolCliente = u.rolCliente, rolEmprendedor = u.rolEmprendedor,
            kycLayer = u.kycLayer, fotoVerificacionEstado = u.fotoVerificacionEstado,
            senescytSriEstado = u.senescytSriEstado, estadoCedula = u.estadoCedula, creadoEn = u.creadoEn,
            negocio = negocio, contratos = contratos,
            solicitudes = solicitudRepository.countByClienteId(id),
            resenas = resenaRepository.countByClienteId(id),
        )
    }

    // ===================================================================
    // Categorías del catálogo maestro (E5) (NUEVO)
    // ===================================================================
    fun listarCategorias(): List<CategoriaResponse> =
        categoriaRepository.findAllByOrderByNombreAsc().map { c ->
            CategoriaResponse(c.id!!, c.nombre, c.icono, c.activa, negocioRepository.countByCategoriaId(c.id!!))
        }

    @Transactional
    fun crearCategoria(req: CrearCategoriaRequest): CategoriaResponse {
        if (categoriaRepository.existsByNombreIgnoreCase(req.nombre)) {
            throw AppException(HttpStatus.CONFLICT, "YA_EXISTE", "Ya existe una categoría con ese nombre")
        }
        val nueva = categoriaRepository.save(Categoria(nombre = req.nombre, icono = req.icono ?: "tag", activa = true))
        return CategoriaResponse(nueva.id!!, nueva.nombre, nueva.icono, nueva.activa, 0)
    }

    @Transactional
    fun alternarCategoria(id: Int): CategoriaResponse {
        val cat = categoriaRepository.findById(id)
            .orElseThrow { AppException(HttpStatus.NOT_FOUND, "NO_ENCONTRADA", "Categoría no encontrada") }
        cat.activa = !cat.activa
        categoriaRepository.save(cat)
        return CategoriaResponse(cat.id!!, cat.nombre, cat.icono, cat.activa, negocioRepository.countByCategoriaId(cat.id!!))
    }

    // ===================================================================
    // Denuncias (E3) (NUEVO)
    // ===================================================================
    fun listarDenuncias(estado: String): List<DenunciaResponse> =
        denunciaRepository.findByEstadoOrderByCreadoEnDesc(estado).map { d ->
            val r = d.reportante!!
            DenunciaResponse(d.id!!, d.estado, ReportanteResponse(r.nombreCompleto, r.correo), d.cedulaReportada, d.motivo, d.creadoEn)
        }

    @Transactional
    fun resolverDenuncia(id: UUID, adminId: UUID, req: ResolverDenunciaRequest): DenunciaResponse {
        val d = denunciaRepository.findById(id)
            .orElseThrow { AppException(HttpStatus.NOT_FOUND, "NO_ENCONTRADA", "Denuncia no encontrada") }

        d.estado = "archivada"
        d.revisadoPor = adminId
        denunciaRepository.save(d)

        registrarLog(adminId, "denuncia_${req.accion}", "denuncias", id.toString())

        val r = d.reportante!!
        return DenunciaResponse(d.id!!, d.estado, ReportanteResponse(r.nombreCompleto, r.correo), d.cedulaReportada, d.motivo, d.creadoEn)
    }

    // ===================================================================
    // Logs de auditoría (E8) (NUEVO) — el backend ya los escribía; esto
    // es lo primero que permite leerlos.
    // ===================================================================
    fun listarLogs(): List<LogAuditoriaResponse> {
        val logs = logRepository.findTop50ByOrderByCreadoEnDesc()
        val cacheAdmins = mutableMapOf<UUID, String>()

        return logs.map { log ->
            val adminId = log.adminId!!
            val nombreAdmin = cacheAdmins.getOrPut(adminId) {
                adminRepository.findById(adminId).map { it.nombre }.orElse("Admin")
            }
            LogAuditoriaResponse(
                id = log.id!!, admin = nombreAdmin, accion = log.accion,
                entidad = log.entidadAfectada, entidadId = log.entidadId,
                detalle = construirDetalleLog(log), creadoEn = log.creadoEn,
            )
        }
    }

    private fun construirDetalleLog(log: AdminLogAuditoria): String = when (log.accion) {
        "kyc_foto_aprobada", "kyc_foto_rechazada" -> "usuario ${nombreDeVerificacion(log.entidadId)}"
        "veto_cedula" -> "cédula ${log.entidadId}"
        else -> log.entidadId ?: ""
    }

    // ===================================================================
    private fun registrarLog(adminId: UUID, accion: String, entidad: String, entidadId: String) {
        logRepository.save(
            AdminLogAuditoria(adminId = adminId, accion = accion, entidadAfectada = entidad, entidadId = entidadId)
        )
    }

    private fun credencialesInvalidas() =
        AppException(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", "Correo o contraseña incorrectos")
}
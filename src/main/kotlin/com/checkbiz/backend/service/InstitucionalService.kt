package com.checkbiz.backend.service

import com.checkbiz.backend.config.InstitucionalClaims
import com.checkbiz.backend.config.JwtService
import com.checkbiz.backend.domain.AlumniVerificacion
import com.checkbiz.backend.dto.AlumniSeguimientoResponse
import com.checkbiz.backend.dto.DashboardB2GResponse
import com.checkbiz.backend.dto.DashboardCacesResponse
import com.checkbiz.backend.dto.InstitucionalAuthResponse
import com.checkbiz.backend.dto.InstitucionalLoginRequest
import com.checkbiz.backend.dto.InstitucionalResponse
import com.checkbiz.backend.dto.NivelAgregadoResponse
import com.checkbiz.backend.exception.AppException
import com.checkbiz.backend.repository.AlumniVerificacionRepository
import com.checkbiz.backend.repository.CuentaInstitucionalRepository
import com.checkbiz.backend.repository.NegocioRepository
import com.checkbiz.backend.repository.UniversidadRepository
import com.checkbiz.backend.util.RimpeSimulador
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

private val NIVELES = listOf("semilla", "asesoria", "formalizado")

@Service
class InstitucionalService(
    private val cuentaRepository: CuentaInstitucionalRepository,
    private val negocioRepository: NegocioRepository,
    private val universidadRepository: UniversidadRepository,
    private val alumniVerificacionRepository: AlumniVerificacionRepository,
    private val negocioService: NegocioService,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder,
) {

    // ===================================================================
    // Login (D1) — rol diferenciado, ruta y JWT separados del público y del
    // admin. Nunca da acceso a datos personales: eso lo bloquea D2 por sí
    // mismo (solo agrega conteos), no un chequeo aparte aquí.
    // ===================================================================
    fun login(req: InstitucionalLoginRequest): InstitucionalAuthResponse {
        val cuenta = cuentaRepository.findByCorreo(req.correo).orElseThrow { credencialesInvalidas() }

        if (!cuenta.activo || !passwordEncoder.matches(req.password, cuenta.passwordHash)) {
            throw credencialesInvalidas()
        }

        return InstitucionalAuthResponse(
            mensaje = "Sesión institucional iniciada",
            institucion = InstitucionalResponse(
                id = cuenta.id!!, nombreInstitucion = cuenta.nombreInstitucion,
                tipo = cuenta.tipo, correo = cuenta.correo,
            ),
            token = jwtService.generarTokenInstitucional(
                InstitucionalClaims(sub = cuenta.id!!, tipo = cuenta.tipo, nombreInstitucion = cuenta.nombreInstitucion)
            ),
        )
    }

    // ===================================================================
    // Dashboard agregado de formalización (D2)
    // ===================================================================

    /**
     * Vista macro para la Cámara de Impuestos / Cámaras de Negocio: solo
     * conteos sobre negocios ya publicados — nunca nombres, dueños ni datos
     * de un negocio en particular. La proyección de recaudación es
     * deliberadamente conservadora: cuenta solo los negocios en nivel
     * "formalizado" y les aplica la cuota fija de Negocio Popular como piso
     * de referencia (no se asume qué tramo RIMPE exacto le toca a cada uno).
     */
    fun dashboardB2G(): DashboardB2GResponse {
        val porNivel = NIVELES.map { nivel ->
            NivelAgregadoResponse(nivel, negocioRepository.countByEstadoPublicacionAndNivelFormalizacion("publicado", nivel))
        }
        val formalizados = porNivel.first { it.nivel == "formalizado" }.totalNegocios

        return DashboardB2GResponse(
            totalNegociosPublicados = negocioRepository.countByEstadoPublicacion("publicado"),
            porNivel = porNivel,
            porCategoria = negocioRepository.contarPublicadosPorCategoria("publicado"),
            proyeccionRecaudacionAnualEstimada = RimpeSimulador.CUOTA_NEGOCIO_POPULAR.multiply(formalizados.toBigDecimal()),
            advertenciaProyeccion = "Proyección conservadora: solo cuenta negocios en nivel Formalizado y aplica la " +
                "cuota mínima de Negocio Popular como piso de referencia. " + RimpeSimulador.ADVERTENCIA,
        )
    }

    // ===================================================================
    // Dashboard CACES (C2) — igual de conservador que D2, pero solo sobre
    // los alumni que ESTA universidad ya verificó como propios.
    // ===================================================================
    @Transactional(readOnly = true)
    fun dashboardCaces(cuentaId: UUID): DashboardCacesResponse {
        val universidad = universidadDeOrThrow(cuentaId)
        val universidadId = universidad.id!!

        val totalAlumniVerificados = alumniVerificacionRepository.countByUniversidadIdAndEstado(universidadId, "verificado")
        val negociosAlumni = negocioRepository.negociosDeAlumniVerificados(universidadId)

        val porNivel = NIVELES.map { nivel ->
            NivelAgregadoResponse(nivel, negociosAlumni.count { it.nivelFormalizacion == nivel }.toLong())
        }
        val formalizados = porNivel.first { it.nivel == "formalizado" }.totalNegocios
        val trustScorePromedio = if (negociosAlumni.isEmpty()) 0.0 else negociosAlumni.map { it.trustScore.toInt() }.average()

        return DashboardCacesResponse(
            nombreUniversidad = universidad.nombre,
            totalAlumniVerificados = totalAlumniVerificados,
            totalConNegocioPublicado = negociosAlumni.size.toLong(),
            porNivel = porNivel,
            trustScorePromedio = trustScorePromedio,
            proyeccionRecaudacionAnualEstimada = RimpeSimulador.CUOTA_NEGOCIO_POPULAR.multiply(formalizados.toBigDecimal()),
            advertenciaProyeccion = "Proyección conservadora: solo cuenta a los alumni que esta universidad ya " +
                "verificó como propios y que tienen un negocio Formalizado. " + RimpeSimulador.ADVERTENCIA,
        )
    }

    // ===================================================================
    // Seguimiento de alumni (C3) — la universidad revisa y decide sobre
    // las solicitudes de verificación que sus propios egresados enviaron.
    // ===================================================================
    @Transactional(readOnly = true)
    fun listarAlumni(cuentaId: UUID): List<AlumniSeguimientoResponse> {
        val universidad = universidadDeOrThrow(cuentaId)
        return alumniVerificacionRepository.findByUniversidadIdOrderByCreadoEnDesc(universidad.id!!).map { it.aSeguimientoResponse() }
    }

    @Transactional
    fun decidirAlumni(cuentaId: UUID, alumniId: UUID, estado: String): AlumniSeguimientoResponse {
        val universidad = universidadDeOrThrow(cuentaId)
        val verificacion = alumniVerificacionRepository.findById(alumniId)
            .orElseThrow { AppException(HttpStatus.NOT_FOUND, "NO_ENCONTRADA", "Solicitud de verificación no encontrada") }

        if (verificacion.universidad?.id != universidad.id) {
            throw AppException(HttpStatus.FORBIDDEN, "NO_AUTORIZADO", "Esta solicitud no pertenece a tu universidad")
        }

        verificacion.estado = estado
        if (estado == "verificado") verificacion.verificadoEn = OffsetDateTime.now()
        alumniVerificacionRepository.save(verificacion)

        // La insignia "Alumni Verificado" (B10) depende de este estado —
        // si el egresado ya tiene un negocio, se reevalúa de inmediato.
        if (estado == "verificado") {
            negocioRepository.findByUsuarioId(verificacion.usuario!!.id!!)?.let { negocioService.actualizarInsignias(it.id!!) }
        }

        return verificacion.aSeguimientoResponse()
    }

    private fun universidadDeOrThrow(cuentaId: UUID) =
        universidadRepository.findByCuentaId(cuentaId)
            ?: throw AppException(
                HttpStatus.FORBIDDEN, "NO_ES_UNIVERSIDAD",
                "Esta sección es exclusiva para cuentas institucionales de tipo universidad"
            )

    private fun AlumniVerificacion.aSeguimientoResponse() = AlumniSeguimientoResponse(
        id = id!!, nombreAlumni = usuario?.nombreCompleto ?: "", estado = estado,
        creadoEn = creadoEn, verificadoEn = verificadoEn,
    )

    private fun credencialesInvalidas() =
        AppException(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", "Correo o contraseña incorrectos")
}

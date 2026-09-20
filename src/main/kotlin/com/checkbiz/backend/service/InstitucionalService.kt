package com.checkbiz.backend.service

import com.checkbiz.backend.config.InstitucionalClaims
import com.checkbiz.backend.config.JwtService
import com.checkbiz.backend.dto.DashboardB2GResponse
import com.checkbiz.backend.dto.InstitucionalAuthResponse
import com.checkbiz.backend.dto.InstitucionalLoginRequest
import com.checkbiz.backend.dto.InstitucionalResponse
import com.checkbiz.backend.dto.NivelAgregadoResponse
import com.checkbiz.backend.exception.AppException
import com.checkbiz.backend.repository.CuentaInstitucionalRepository
import com.checkbiz.backend.repository.NegocioRepository
import com.checkbiz.backend.util.RimpeSimulador
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

private val NIVELES = listOf("semilla", "asesoria", "formalizado")

@Service
class InstitucionalService(
    private val cuentaRepository: CuentaInstitucionalRepository,
    private val negocioRepository: NegocioRepository,
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

    private fun credencialesInvalidas() =
        AppException(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", "Correo o contraseña incorrectos")
}

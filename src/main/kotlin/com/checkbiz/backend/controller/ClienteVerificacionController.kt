package com.checkbiz.backend.controller

import com.checkbiz.backend.dto.CapaVerificacionResponse
import com.checkbiz.backend.repository.NegocioRepository
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class NegocioCedulaPublicaResponse(
    val nombreComercial: String,
    val slug: String,
    val trustScore: Short,
    val nivelFormalizacion: String,
    val capasVerificacion: List<CapaVerificacionResponse>,
)

data class ConsultaCedulaPublicaResponse(val negocio: NegocioCedulaPublicaResponse?)
data class ConsultaCedulaRequest(val cedula: String)

/** Solo confirma la vinculación con un negocio PUBLICADO; nunca consulta cuentas privadas. */
@RestController
@RequestMapping("/api/cliente/verificacion")
class ClienteVerificacionController(private val negocioRepository: NegocioRepository) {

    @PostMapping("/cedula")
    @Transactional(readOnly = true)
    fun consultarCedula(@RequestBody req: ConsultaCedulaRequest): ConsultaCedulaPublicaResponse {
        if (!Regex("^[0-9]{10}$").matches(req.cedula)) return ConsultaCedulaPublicaResponse(null)
        val negocio = negocioRepository.findByUsuario_CedulaAndEstadoPublicacion(req.cedula, "publicado")
            ?: return ConsultaCedulaPublicaResponse(null)
        val usuario = negocio.usuario!!
        return ConsultaCedulaPublicaResponse(NegocioCedulaPublicaResponse(
            nombreComercial = negocio.nombreComercial,
            slug = negocio.slug,
            trustScore = negocio.trustScore,
            nivelFormalizacion = negocio.nivelFormalizacion,
            capasVerificacion = listOf(
                CapaVerificacionResponse("Cédula", usuario.kycLayer >= 1),
                CapaVerificacionResponse("Correo", usuario.kycLayer >= 2),
                CapaVerificacionResponse("Cédula revisada", usuario.fotoVerificacionEstado == "aprobada"),
                CapaVerificacionResponse("SENESCYT/SRI", usuario.senescytSriEstado == "verificado"),
            ),
        ))
    }
}

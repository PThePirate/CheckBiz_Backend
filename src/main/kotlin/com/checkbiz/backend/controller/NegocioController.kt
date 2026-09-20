package com.checkbiz.backend.controller

import com.checkbiz.backend.config.CheckBizAuthenticationToken
import com.checkbiz.backend.config.UsuarioClaims
import com.checkbiz.backend.dto.*
import com.checkbiz.backend.service.NegocioService
import com.checkbiz.backend.util.RimpeSimulador
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/negocio")
class NegocioController(private val negocioService: NegocioService) {

    private fun usuarioActual(): UsuarioClaims {
        val auth = SecurityContextHolder.getContext().authentication as CheckBizAuthenticationToken
        return auth.principal as UsuarioClaims
    }

    // --- Negocio (B3) ---
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun crear(@Valid @RequestBody req: CrearNegocioRequest): NegocioResponse =
        negocioService.crear(usuarioActual().sub, req)

    @GetMapping("/mio")
    fun obtenerMio(): NegocioResponse = negocioService.obtenerMiNegocio(usuarioActual().sub)

    @PutMapping("/mio")
    fun actualizar(@Valid @RequestBody req: ActualizarNegocioRequest): NegocioResponse =
        negocioService.actualizar(usuarioActual().sub, req)

    @PatchMapping("/mio/publicacion")
    fun cambiarPublicacion(@RequestParam publicar: Boolean): NegocioResponse =
        negocioService.cambiarPublicacion(usuarioActual().sub, publicar)

    // --- Bandeja de solicitudes (B5) ---
    @GetMapping("/mio/solicitudes")
    fun misSolicitudesRecibidas(): List<SolicitudRecibidaResponse> =
        negocioService.misSolicitudesRecibidas(usuarioActual().sub)

    @PatchMapping("/mio/solicitudes/{id}/estado")
    fun actualizarEstadoSolicitud(
        @PathVariable id: UUID,
        @Valid @RequestBody req: ActualizarEstadoSolicitudRequest,
    ): SolicitudRecibidaResponse = negocioService.actualizarEstadoSolicitud(usuarioActual().sub, id, req.estado)

    // --- Reputación y Trust Score (B6) ---
    @GetMapping("/mio/reputacion")
    fun obtenerReputacion(): ReputacionResponse = negocioService.obtenerReputacion(usuarioActual().sub)

    @GetMapping("/mio/resenas")
    fun listarMisResenas(): List<ResenaDetalleResponse> = negocioService.listarMisResenas(usuarioActual().sub)

    @PatchMapping("/mio/resenas/{id}/respuesta")
    fun responderResena(
        @PathVariable id: UUID,
        @Valid @RequestBody req: ResponderResenaRequest,
    ): ResenaDetalleResponse = negocioService.responderResena(usuarioActual().sub, id, req.respuesta)

    // --- Catálogo (B4) ---
    @GetMapping("/mio/catalogo")
    fun listarCatalogo(): List<ItemCatalogoResponse> = negocioService.listarCatalogo(usuarioActual().sub)

    @PostMapping("/mio/catalogo")
    @ResponseStatus(HttpStatus.CREATED)
    fun crearItem(@Valid @RequestBody req: CrearItemCatalogoRequest): ItemCatalogoResponse =
        negocioService.crearItem(usuarioActual().sub, req)

    @PatchMapping("/mio/catalogo/{id}")
    fun actualizarItem(
        @PathVariable id: UUID,
        @Valid @RequestBody req: ActualizarItemCatalogoRequest,
    ): ItemCatalogoResponse = negocioService.actualizarItem(usuarioActual().sub, id, req)

    @DeleteMapping("/mio/catalogo/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun eliminarItem(@PathVariable id: UUID) {
        negocioService.eliminarItem(usuarioActual().sub, id)
    }

    // --- QR de verificación física (B11) ---
    @GetMapping("/mio/qr")
    fun obtenerQr(): QrResponse = negocioService.obtenerOCrearQr(usuarioActual().sub)

    // --- Ruta de Formalización + Simulador RIMPE (B8) ---
    @GetMapping("/mio/formalizacion")
    fun obtenerFormalizacion(): RutaFormalizacionResponse =
        negocioService.obtenerRutaFormalizacion(usuarioActual().sub)

    @PatchMapping("/mio/formalizacion/rimpe")
    fun marcarRimpeRegistrado(@Valid @RequestBody req: MarcarRimpeRegistradoRequest): RutaFormalizacionResponse =
        negocioService.marcarRimpeRegistrado(usuarioActual().sub, req.completado)

    // Calculadora pura — no depende del negocio del usuario, solo exige sesión.
    @PostMapping("/rimpe/simular")
    fun simularRimpe(@Valid @RequestBody req: SimulacionRimpeRequest): SimulacionRimpeResponse =
        RimpeSimulador.simular(req.ingresosAnuales)

    // --- Panel de Analítica (B7) ---
    @GetMapping("/mio/analitica")
    fun obtenerAnalitica(): AnaliticaNegocioResponse = negocioService.obtenerAnalitica(usuarioActual().sub)

    // --- Suscripción / Planes (B9) ---
    @GetMapping("/planes")
    fun listarPlanes(): List<PlanResponse> = negocioService.listarPlanes()

    @GetMapping("/mio/suscripcion")
    fun obtenerMiSuscripcion(): SuscripcionResponse = negocioService.obtenerMiSuscripcion(usuarioActual().sub)

    @PostMapping("/mio/suscripcion/checkout")
    fun cambiarPlan(@Valid @RequestBody req: CambiarPlanRequest): SuscripcionResponse =
        negocioService.cambiarPlan(usuarioActual().sub, req)
}
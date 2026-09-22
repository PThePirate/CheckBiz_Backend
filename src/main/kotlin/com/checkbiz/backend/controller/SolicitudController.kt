package com.checkbiz.backend.controller

import com.checkbiz.backend.config.CheckBizAuthenticationToken
import com.checkbiz.backend.config.UsuarioClaims
import com.checkbiz.backend.dto.*
import com.checkbiz.backend.service.SolicitudService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/solicitudes")
class SolicitudController(private val solicitudService: SolicitudService) {

    private fun usuarioActual(): UsuarioClaims {
        val auth = SecurityContextHolder.getContext().authentication as CheckBizAuthenticationToken
        return auth.principal as UsuarioClaims
    }

    // A7 — crear solicitud
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun crear(@Valid @RequestBody req: CrearSolicitudRequest): SolicitudResponse =
        solicitudService.crear(usuarioActual().sub, req)

    // Bandeja del cliente (base de A9, necesaria para A8)
    @GetMapping("/mias")
    fun misSolicitudes(): List<SolicitudResponse> = solicitudService.misSolicitudes(usuarioActual().sub)

    // A8 — confirmar recepción
    @PatchMapping("/{id}/confirmar")
    fun confirmar(@PathVariable id: UUID): SolicitudResponse =
        solicitudService.confirmar(usuarioActual().sub, id)

    // A8 — dejar reseña (solo tras confirmar)
    @PostMapping("/{id}/resena")
    @ResponseStatus(HttpStatus.CREATED)
    fun dejarResena(@PathVariable id: UUID, @Valid @RequestBody req: CrearResenaRequest): SolicitudResponse =
        solicitudService.dejarResena(usuarioActual().sub, id, req)

    // Chat interno de la solicitud (reemplaza el botón de WhatsApp) —
    // accesible tanto por el cliente dueño de la solicitud como por el
    // negocio (dueño o colaborador) que la recibió.
    @GetMapping("/{id}/mensajes")
    fun listarMensajes(@PathVariable id: UUID): List<MensajeResponse> =
        solicitudService.listarMensajes(usuarioActual().sub, id)

    @PostMapping("/{id}/mensajes")
    @ResponseStatus(HttpStatus.CREATED)
    fun enviarMensaje(@PathVariable id: UUID, @Valid @RequestBody req: EnviarMensajeRequest): MensajeResponse =
        solicitudService.enviarMensaje(usuarioActual().sub, id, req)
}
package com.checkbiz.backend.controller

import com.checkbiz.backend.dto.NegocioPublicoResponse
import com.checkbiz.backend.dto.NegocioResumenPublicoResponse
import com.checkbiz.backend.service.NegocioService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Rutas públicas de negocios — sin autenticación, cualquiera puede verlas.
 * Separado de NegocioController (que exige login del dueño) a propósito,
 * para que la frontera entre "público" y "requiere sesión" sea explícita
 * en el propio nombre del archivo, no solo en la configuración de rutas.
 */
@RestController
@RequestMapping("/api/negocios")
class NegocioPublicoController(private val negocioService: NegocioService) {

    // A4/A5 — búsqueda y resultados. Todos los filtros son opcionales.
    @GetMapping
    fun buscar(
        @RequestParam(required = false) categoriaId: Int?,
        @RequestParam(required = false) ciudad: String?,
        @RequestParam(required = false) nivel: String?,
        @RequestParam(required = false) texto: String?,
    ): List<NegocioResumenPublicoResponse> =
        negocioService.buscarPublicados(categoriaId, ciudad, nivel, texto)

    @GetMapping("/ciudades-disponibles")
    fun ciudadesDisponibles(): List<String> = negocioService.ciudadesDisponibles()

    // A6 — Mini Landing Page pública. Solo devuelve negocios publicados.
    @GetMapping("/publico/{slug}")
    fun obtenerPorSlug(@PathVariable slug: String): NegocioPublicoResponse =
        negocioService.obtenerPublicoPorSlug(slug)
}
package com.checkbiz.backend.controller

import com.checkbiz.backend.dto.CategoriaResumenResponse
import com.checkbiz.backend.dto.EscaneoQrResponse
import com.checkbiz.backend.dto.NegocioPublicoResponse
import com.checkbiz.backend.dto.NegocioResumenPublicoResponse
import com.checkbiz.backend.service.ArchivoService
import com.checkbiz.backend.service.NegocioService
import org.springframework.http.ResponseEntity
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
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
class NegocioPublicoController(
    private val negocioService: NegocioService,
    private val archivoService: ArchivoService,
) {

    @GetMapping("/videos/{nombre}")
    fun obtenerVideo(@PathVariable nombre: String): ResponseEntity<ByteArray> =
        ResponseEntity.ok().contentType(MediaType.parseMediaType("video/mp4")).body(archivoService.leerVideoNegocio(nombre))

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

    // Catálogo maestro de categorías activas — lo usan los filtros de
    // búsqueda pública (A4/A5), así que no puede exigir sesión. Antes vivía
    // en NegocioController (privado), lo que devolvía 401 a cualquiera que
    // no hubiera iniciado sesión.
    @GetMapping("/categorias-disponibles")
    fun categoriasDisponibles(): List<CategoriaResumenResponse> = negocioService.categoriasDisponibles()

    // A6 — Mini Landing Page pública. Solo devuelve negocios publicados.
    // Cada llamada real cuenta como una visita al perfil (B7).
    @GetMapping("/publico/{slug}")
    fun obtenerPorSlug(@PathVariable slug: String): NegocioPublicoResponse =
        negocioService.obtenerPublicoPorSlug(slug, (org.springframework.security.core.context.SecurityContextHolder.getContext().authentication?.principal as? com.checkbiz.backend.config.UsuarioClaims)?.sub)

    // B11 — el frontend llama esto justo al abrir /qr/{codigo} (lo que
    // escanea el QR físico), antes de redirigir a la Mini Landing Page.
    @PostMapping("/qr/{codigo}/escaneo")
    fun registrarEscaneoQr(@PathVariable codigo: String): EscaneoQrResponse =
        negocioService.registrarEscaneoQr(codigo)

    // A6/B3/B4 — logo, portada y fotos de catálogo se sirven aquí, público
    // y sin autenticación, desde un subdirectorio separado del de KYC/perfil
    // (ver ArchivoService.guardarImagenNegocio) para que esto nunca pueda
    // alcanzar un documento de identidad.
    @GetMapping("/imagenes/{nombre}")
    fun obtenerImagen(@PathVariable nombre: String): ResponseEntity<ByteArray> {
        val (bytes, tipo) = archivoService.leerImagenNegocio(nombre)
        return ResponseEntity.ok().contentType(tipo).body(bytes)
    }
}

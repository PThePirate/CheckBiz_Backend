package com.checkbiz.backend.controller

import com.checkbiz.backend.service.NegocioService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * F2 — sitemap.xml dinámico: incluye las páginas públicas fijas y el perfil
 * público de cada negocio publicado (nunca borradores). checkbiz.frontend-url
 * apunta al dominio real del frontend, no a este backend, porque las URLs
 * de un sitemap son las que un buscador debe indexar y visitar.
 */
@RestController
class SitemapController(
    private val negocioService: NegocioService,
    @Value("\${checkbiz.frontend-url}") private val frontendUrl: String,
) {
    private val paginasEstaticas = listOf("", "/buscar", "/como-funciona", "/universidades", "/ayuda", "/contrato")

    @GetMapping("/sitemap.xml", produces = [MediaType.APPLICATION_XML_VALUE])
    fun sitemap(): String {
        val urlsEstaticas = paginasEstaticas.joinToString("") { path -> url("$frontendUrl$path") }
        val urlsNegocios = negocioService.slugsPublicados()
            .joinToString("") { slug -> url("$frontendUrl/negocio/publico/$slug") }

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">" +
            urlsEstaticas + urlsNegocios +
            "</urlset>"
    }

    private fun url(loc: String) = "<url><loc>$loc</loc></url>"
}

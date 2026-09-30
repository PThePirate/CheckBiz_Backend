package com.checkbiz.backend.service

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/** Retira imágenes de decisiones anteriores que aún existían en disco. */
@Component
class KycMediaCleanup(
    private val jdbcTemplate: JdbcTemplate,
    private val archivoService: ArchivoService,
    private val negocioService: NegocioService,
) {
    private val log = LoggerFactory.getLogger(KycMediaCleanup::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun purgarRevisadas() {
        val revisadas = jdbcTemplate.query(
            """SELECT id, foto_url, foto_reverso_url FROM verificaciones_foto
               WHERE estado <> 'en_revision' AND (foto_url <> '' OR foto_reverso_url IS NOT NULL)"""
        ) { rs, _ ->
            Triple(rs.getObject("id", UUID::class.java), rs.getString("foto_url"), rs.getString("foto_reverso_url"))
        }
        revisadas.forEach { (id, frente, reverso) ->
            try {
                if (!frente.isNullOrBlank()) archivoService.eliminarImagenPrivada(frente)
                if (!reverso.isNullOrBlank()) archivoService.eliminarImagenPrivada(reverso)
                jdbcTemplate.update("UPDATE verificaciones_foto SET foto_url = '', foto_reverso_url = NULL WHERE id = ?", id)
            } catch (ex: Exception) {
                log.error("No se pudieron purgar los archivos KYC revisados de {}", id, ex)
            }
        }
        jdbcTemplate.query("SELECT id FROM negocios") { rs, _ -> rs.getObject(1, UUID::class.java) }
            .forEach { id ->
                try { negocioService.recalcularTrustScore(id) }
                catch (ex: Exception) { log.error("No se pudo recalcular Trust Score de {}", id, ex) }
            }
    }
}

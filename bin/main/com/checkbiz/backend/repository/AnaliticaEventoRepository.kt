package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.AnaliticaEvento
import org.springframework.data.jpa.repository.JpaRepository
import java.time.OffsetDateTime
import java.util.UUID

interface AnaliticaEventoRepository : JpaRepository<AnaliticaEvento, Long> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "INSERT INTO analitica_eventos (negocio_id, tipo_evento, visitante_id, dia_visita, creado_en) VALUES (:negocioId, 'visita_validada', :visitanteId, (CURRENT_TIMESTAMP AT TIME ZONE 'America/Guayaquil')::date, CURRENT_TIMESTAMP) ON CONFLICT DO NOTHING", nativeQuery = true)
    fun registrarVisitaUnica(@org.springframework.data.repository.query.Param("negocioId") negocioId: UUID, @org.springframework.data.repository.query.Param("visitanteId") visitanteId: UUID): Int
    fun countByNegocioIdAndTipoEvento(negocioId: UUID, tipoEvento: String): Long

    fun countByNegocioIdAndTipoEventoAndCreadoEnBetween(
        negocioId: UUID, tipoEvento: String, desde: OffsetDateTime, hasta: OffsetDateTime,
    ): Long

    // Trae ambos tipos de evento del panel B7 juntos; la serie diaria se arma
    // agrupando por día en Kotlin (el volumen esperado es bajo, así que no
    // hace falta una consulta agregada por día en SQL).
    fun findByNegocioIdAndTipoEventoInAndCreadoEnAfter(
        negocioId: UUID, tipos: List<String>, desde: OffsetDateTime,
    ): List<AnaliticaEvento>
}

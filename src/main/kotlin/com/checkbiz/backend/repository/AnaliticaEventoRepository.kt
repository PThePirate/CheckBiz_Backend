package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.AnaliticaEvento
import org.springframework.data.jpa.repository.JpaRepository
import java.time.OffsetDateTime
import java.util.UUID

interface AnaliticaEventoRepository : JpaRepository<AnaliticaEvento, Long> {
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

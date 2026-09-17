package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.Resena
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ResenaRepository : JpaRepository<Resena, UUID> {
    fun countByClienteId(clienteId: UUID): Long
    fun countByNegocioId(negocioId: UUID): Long
    fun existsBySolicitudId(solicitudId: UUID): Boolean
    fun findBySolicitudId(solicitudId: UUID): Resena?
    fun findByNegocioIdOrderByCreadoEnDesc(negocioId: UUID): List<Resena>

    @Query("SELECT COALESCE(AVG(r.estrellas), 0) FROM Resena r WHERE r.negocio.id = :negocioId")
    fun promedioEstrellas(@Param("negocioId") negocioId: UUID): Double
}
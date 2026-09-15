package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.Negocio
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NegocioRepository : JpaRepository<Negocio, UUID> {
    fun countByEstadoPublicacion(estado: String): Long
    fun countByCategoriaId(categoriaId: Int): Long
    fun findByUsuarioId(usuarioId: UUID): Negocio?
}
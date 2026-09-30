package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.CatalogoItem
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CatalogoItemRepository : JpaRepository<CatalogoItem, UUID> {
    fun findByNegocioIdOrderByOrdenAsc(negocioId: UUID): List<CatalogoItem>
    fun countByNegocioIdAndActivoTrue(negocioId: UUID): Long
}
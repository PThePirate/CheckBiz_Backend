package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.RutaFormalizacion
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RutaFormalizacionRepository : JpaRepository<RutaFormalizacion, UUID> {
    fun findByNegocioId(negocioId: UUID): List<RutaFormalizacion>
    fun findByNegocioIdAndNivelAndRequisito(negocioId: UUID, nivel: String, requisito: String): RutaFormalizacion?
}

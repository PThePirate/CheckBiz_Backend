package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.NegocioInsignia
import com.checkbiz.backend.domain.NegocioInsigniaId
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NegocioInsigniaRepository : JpaRepository<NegocioInsignia, NegocioInsigniaId> {
    fun findByNegocioIdOrderByObtenidaEnDesc(negocioId: UUID): List<NegocioInsignia>
}

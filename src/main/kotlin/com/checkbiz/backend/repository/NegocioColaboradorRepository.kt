package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.NegocioColaborador
import com.checkbiz.backend.domain.NegocioColaboradorId
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NegocioColaboradorRepository : JpaRepository<NegocioColaborador, NegocioColaboradorId> {
    fun findByNegocioIdOrderByAgregadoEnAsc(negocioId: UUID): List<NegocioColaborador>
    fun findByUsuarioId(usuarioId: UUID): NegocioColaborador?
}

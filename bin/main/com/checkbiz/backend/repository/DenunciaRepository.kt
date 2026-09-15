package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.Denuncia
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DenunciaRepository : JpaRepository<Denuncia, UUID> {
    fun findByEstadoOrderByCreadoEnDesc(estado: String): List<Denuncia>
}
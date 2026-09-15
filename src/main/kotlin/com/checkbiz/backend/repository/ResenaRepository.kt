package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.Resena
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ResenaRepository : JpaRepository<Resena, UUID> {
    fun countByClienteId(clienteId: UUID): Long
}
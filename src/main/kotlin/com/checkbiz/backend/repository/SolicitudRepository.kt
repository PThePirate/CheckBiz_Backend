package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.Solicitud
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SolicitudRepository : JpaRepository<Solicitud, UUID> {
    fun countByClienteId(clienteId: UUID): Long
}
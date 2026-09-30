package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.MensajeSolicitud
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MensajeSolicitudRepository : JpaRepository<MensajeSolicitud, UUID> {
    fun findBySolicitudIdOrderByCreadoEnAsc(solicitudId: UUID): List<MensajeSolicitud>
}

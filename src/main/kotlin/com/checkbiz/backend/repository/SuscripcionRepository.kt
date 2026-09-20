package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.Suscripcion
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SuscripcionRepository : JpaRepository<Suscripcion, UUID> {
    fun findByNegocioId(negocioId: UUID): Suscripcion?
}

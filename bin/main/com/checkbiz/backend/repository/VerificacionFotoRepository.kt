package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.VerificacionFoto
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface VerificacionFotoRepository : JpaRepository<VerificacionFoto, UUID> {
    fun findByEstadoOrderByCreadoEnAsc(estado: String): List<VerificacionFoto>
    fun countByEstado(estado: String): Long
}
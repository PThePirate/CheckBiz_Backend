package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.CuentaInstitucional
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface CuentaInstitucionalRepository : JpaRepository<CuentaInstitucional, UUID> {
    fun findByCorreo(correo: String): Optional<CuentaInstitucional>
}

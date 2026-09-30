package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.Universidad
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UniversidadRepository : JpaRepository<Universidad, Int> {
    fun findAllByActivaTrueOrderByNombreAsc(): List<Universidad>
    fun findByCuentaId(cuentaId: UUID): Universidad?
}

package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.VetoCedula
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface VetoCedulaRepository : JpaRepository<VetoCedula, UUID> {
    fun findByCedula(cedula: String): Optional<VetoCedula>
    fun existsByCedula(cedula: String): Boolean
}

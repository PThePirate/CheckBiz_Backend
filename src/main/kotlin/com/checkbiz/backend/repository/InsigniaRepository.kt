package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.Insignia
import org.springframework.data.jpa.repository.JpaRepository

interface InsigniaRepository : JpaRepository<Insignia, Int> {
    fun findByNombre(nombre: String): Insignia?
}

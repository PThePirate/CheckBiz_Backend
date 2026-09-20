package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.Plan
import org.springframework.data.jpa.repository.JpaRepository

interface PlanRepository : JpaRepository<Plan, Int> {
    fun findByNombre(nombre: String): Plan?
    fun findAllByOrderByPrecioMensualAsc(): List<Plan>
}

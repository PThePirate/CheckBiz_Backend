package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.Categoria
import org.springframework.data.jpa.repository.JpaRepository

interface CategoriaRepository : JpaRepository<Categoria, Int> {
    fun existsByNombreIgnoreCase(nombre: String): Boolean
    fun findAllByOrderByNombreAsc(): List<Categoria>
}
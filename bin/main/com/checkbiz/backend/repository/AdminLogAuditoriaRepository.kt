package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.AdminLogAuditoria
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface AdminLogAuditoriaRepository : JpaRepository<AdminLogAuditoria, Long> {
    fun findTop50ByOrderByCreadoEnDesc(): List<AdminLogAuditoria>

    /** Límite dinámico vía Pageable: PageRequest.of(0, limite). */
    fun findAllByOrderByCreadoEnDesc(pageable: Pageable): List<AdminLogAuditoria>
}
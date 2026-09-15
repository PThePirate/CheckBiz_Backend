package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.AdminLogAuditoria
import org.springframework.data.jpa.repository.JpaRepository

interface AdminLogAuditoriaRepository : JpaRepository<AdminLogAuditoria, Long>

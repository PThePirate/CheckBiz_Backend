package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.Admin
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface AdminRepository : JpaRepository<Admin, UUID> {
    fun findByCorreo(correo: String): Optional<Admin>
}

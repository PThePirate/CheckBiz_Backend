package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.Usuario
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface UsuarioRepository : JpaRepository<Usuario, UUID> {
    fun findByCorreo(correo: String): Optional<Usuario>
    fun findByCedula(cedula: String): Optional<Usuario>
    fun existsByCedula(cedula: String): Boolean
    fun existsByCorreo(correo: String): Boolean
}

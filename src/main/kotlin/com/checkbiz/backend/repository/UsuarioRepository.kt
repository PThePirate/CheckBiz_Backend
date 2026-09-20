package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.Usuario
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface UsuarioRepository : JpaRepository<Usuario, UUID> {
    fun findByCorreo(correo: String): Optional<Usuario>
    fun findByCedula(cedula: String): Optional<Usuario>
    fun existsByCedula(cedula: String): Boolean
    fun existsByCorreo(correo: String): Boolean
    fun countByKycLayer(kycLayer: Short): Long

    // JwtAuthFilter la usa en cada request para que un veto invalide de
    // inmediato las sesiones ya emitidas — sin esto, un JWT firmado antes
    // del veto seguía funcionando hasta que expiraba (hasta 7 días).
    @Query("SELECT u.estadoCedula FROM Usuario u WHERE u.id = :id")
    fun estadoCedulaDe(@Param("id") id: UUID): String?

    @Query(
        """
        SELECT u FROM Usuario u
        WHERE LOWER(u.nombreCompleto) LIKE LOWER(CONCAT('%', :q, '%'))
           OR u.cedula LIKE CONCAT('%', :q, '%')
           OR LOWER(u.correo) LIKE LOWER(CONCAT('%', :q, '%'))
        ORDER BY u.nombreCompleto ASC
        """
    )
    fun buscar(@Param("q") q: String): List<Usuario>
}
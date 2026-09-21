package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.AlumniVerificacion
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AlumniVerificacionRepository : JpaRepository<AlumniVerificacion, UUID> {
    fun findByUsuarioIdAndUniversidadId(usuarioId: UUID, universidadId: Int): AlumniVerificacion?
    fun findByUsuarioIdOrderByCreadoEnDesc(usuarioId: UUID): List<AlumniVerificacion>
    fun findByUniversidadIdOrderByCreadoEnDesc(universidadId: Int): List<AlumniVerificacion>
    fun countByUniversidadIdAndEstado(universidadId: Int, estado: String): Long
    fun existsByUsuarioIdAndEstado(usuarioId: UUID, estado: String): Boolean
}

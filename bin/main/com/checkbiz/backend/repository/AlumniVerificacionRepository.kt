package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.AlumniVerificacion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface AlumniVerificacionRepository : JpaRepository<AlumniVerificacion, UUID> {
    @Query("SELECT a FROM AlumniVerificacion a JOIN FETCH a.universidad WHERE a.usuario.id IN :usuarioIds AND a.estado = 'verificado'")
    fun findVerificadasByUsuarioIds(@Param("usuarioIds") usuarioIds: Set<UUID>): List<AlumniVerificacion>
    fun findByUsuarioIdAndUniversidadId(usuarioId: UUID, universidadId: Int): AlumniVerificacion?
    fun findByUsuarioIdOrderByCreadoEnDesc(usuarioId: UUID): List<AlumniVerificacion>
    fun findByUniversidadIdOrderByCreadoEnDesc(universidadId: Int): List<AlumniVerificacion>
    fun countByUniversidadIdAndEstado(universidadId: Int, estado: String): Long
    fun existsByUsuarioIdAndEstado(usuarioId: UUID, estado: String): Boolean
}

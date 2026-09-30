package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.FotoPerfilReciente
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FotoPerfilRecienteRepository : JpaRepository<FotoPerfilReciente, UUID> {
    fun findByUsuarioIdOrderByCreadoEnDesc(usuarioId: UUID): List<FotoPerfilReciente>
    fun findByIdAndUsuarioId(id: UUID, usuarioId: UUID): FotoPerfilReciente?
}

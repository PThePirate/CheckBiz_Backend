package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.OtpVerificacion
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface OtpVerificacionRepository : JpaRepository<OtpVerificacion, UUID> {
    fun findFirstByUsuarioIdAndVerificadoFalseOrderByCreadoEnDesc(usuarioId: UUID): Optional<OtpVerificacion>
}

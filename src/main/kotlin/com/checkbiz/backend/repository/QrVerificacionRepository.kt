package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.QrVerificacion
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface QrVerificacionRepository : JpaRepository<QrVerificacion, UUID> {
    fun findByNegocioId(negocioId: UUID): QrVerificacion?
    fun findByCodigo(codigo: String): QrVerificacion?
    fun existsByCodigo(codigo: String): Boolean
}

package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.ContratoAdhesion
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ContratoAdhesionRepository : JpaRepository<ContratoAdhesion, UUID>

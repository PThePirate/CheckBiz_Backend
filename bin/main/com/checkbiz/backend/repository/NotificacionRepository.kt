package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.Notificacion
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificacionRepository : JpaRepository<Notificacion, UUID>

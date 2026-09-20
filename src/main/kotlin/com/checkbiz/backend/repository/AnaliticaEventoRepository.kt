package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.AnaliticaEvento
import org.springframework.data.jpa.repository.JpaRepository

interface AnaliticaEventoRepository : JpaRepository<AnaliticaEvento, Long>

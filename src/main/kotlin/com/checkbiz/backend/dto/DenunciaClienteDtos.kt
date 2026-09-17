package com.checkbiz.backend.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.UUID

// ---------------------------------------------------------------------
// Reportar un negocio (A11)
// ---------------------------------------------------------------------
data class CrearDenunciaRequest(
    @field:NotBlank
    val negocioSlug: String,

    @field:NotBlank @field:Size(min = 10, max = 1000)
    val motivo: String,
)

data class DenunciaCreadaResponse(
    val id: UUID,
    val estado: String,
    val creadoEn: OffsetDateTime,
)
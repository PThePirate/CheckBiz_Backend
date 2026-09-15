package com.checkbiz.backend.domain

import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "ruta_formalizacion")
class RutaFormalizacion(
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "negocio_id", nullable = false)
    var negocio: Negocio? = null,

    @Column(nullable = false, length = 20)
    var nivel: String = "semilla",

    @Column(nullable = false, length = 150)
    var requisito: String = "",

    @Column(nullable = false)
    var completado: Boolean = false,

    @Column(name = "completado_en")
    var completadoEn: OffsetDateTime? = null,
)

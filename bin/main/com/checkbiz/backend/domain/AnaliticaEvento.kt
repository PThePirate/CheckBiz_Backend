package com.checkbiz.backend.domain

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "analitica_eventos")
class AnaliticaEvento(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "negocio_id", nullable = false)
    var negocio: Negocio? = null,

    @Column(name = "tipo_evento", nullable = false, length = 25)
    var tipoEvento: String = "",

    @Column(name = "creado_en", nullable = false)
    var creadoEn: OffsetDateTime = OffsetDateTime.now(),
)

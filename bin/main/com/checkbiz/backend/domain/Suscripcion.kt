package com.checkbiz.backend.domain

import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "suscripciones")
class Suscripcion(
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "negocio_id", nullable = false, unique = true)
    var negocio: Negocio? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    var plan: Plan? = null,

    @Column(nullable = false, length = 10)
    var ciclo: String = "mensual",

    @Column(nullable = false, length = 15)
    var estado: String = "activa",

    @Column(name = "inicia_en", nullable = false)
    var iniciaEn: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "vence_en")
    var venceEn: OffsetDateTime? = null,
)

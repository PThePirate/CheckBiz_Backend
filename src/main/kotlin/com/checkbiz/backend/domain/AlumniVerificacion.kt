package com.checkbiz.backend.domain

import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(
    name = "alumni_verificacion",
    uniqueConstraints = [UniqueConstraint(columnNames = ["usuario_id", "universidad_id"])]
)
class AlumniVerificacion(
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    var usuario: Usuario? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "universidad_id", nullable = false)
    var universidad: Universidad? = null,

    @Column(nullable = false, length = 20)
    var estado: String = "pendiente",

    @Column(name = "verificado_en")
    var verificadoEn: OffsetDateTime? = null,

    @Column(name = "creado_en", nullable = false)
    var creadoEn: OffsetDateTime = OffsetDateTime.now(),
)

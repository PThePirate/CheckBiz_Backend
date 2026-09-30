package com.checkbiz.backend.domain

import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "notificaciones")
class Notificacion(
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    var usuario: Usuario? = null,

    @Column(nullable = false, length = 30)
    var tipo: String = "",

    @Column(nullable = false, length = 150)
    var titulo: String = "",

    @Column(columnDefinition = "text")
    var mensaje: String? = null,

    @Column(nullable = false)
    var leida: Boolean = false,

    @Column(name = "creado_en", nullable = false)
    var creadoEn: OffsetDateTime = OffsetDateTime.now(),
)

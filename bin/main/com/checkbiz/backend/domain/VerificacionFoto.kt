package com.checkbiz.backend.domain

import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "verificaciones_foto")
class VerificacionFoto(
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    var usuario: Usuario? = null,

    @Column(name = "foto_url", nullable = false, columnDefinition = "text")
    var fotoUrl: String = "",

    @Column(nullable = false, length = 20)
    var estado: String = "en_revision",

    @Column(name = "motivo_rechazo", columnDefinition = "text")
    var motivoRechazo: String? = null,

    @Column(name = "revisado_por", columnDefinition = "uuid")
    var revisadoPor: UUID? = null,

    @Column(name = "revisado_en")
    var revisadoEn: OffsetDateTime? = null,

    @Column(name = "creado_en", nullable = false)
    var creadoEn: OffsetDateTime = OffsetDateTime.now(),
)

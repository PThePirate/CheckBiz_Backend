package com.checkbiz.backend.domain

import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "denuncias")
class Denuncia(
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reportante_id", nullable = false)
    var reportante: Usuario? = null,

    @Column(name = "cedula_reportada", nullable = false, length = 10)
    var cedulaReportada: String = "",

    @Column(nullable = false, columnDefinition = "text")
    var motivo: String = "",

    @Column(nullable = false, length = 20)
    var estado: String = "abierta",

    @Column(name = "revisado_por", columnDefinition = "uuid")
    var revisadoPor: UUID? = null,

    @Column(name = "creado_en", nullable = false)
    var creadoEn: OffsetDateTime = OffsetDateTime.now(),
)

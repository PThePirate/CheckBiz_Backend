package com.checkbiz.backend.domain

import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "solicitudes")
class Solicitud(
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "negocio_id", nullable = false)
    var negocio: Negocio? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    var cliente: Usuario? = null,

    @Column(nullable = false, columnDefinition = "text")
    var descripcion: String = "",

    @Column(name = "fecha_estimada")
    var fechaEstimada: LocalDate? = null,

    @Column(nullable = false, length = 20)
    var estado: String = "enviada",

    @Column(name = "creado_en", nullable = false)
    var creadoEn: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "confirmada_en")
    var confirmadaEn: OffsetDateTime? = null,

    @Column(name = "actualizado_en", nullable = false)
    var actualizadoEn: OffsetDateTime = OffsetDateTime.now(),
)

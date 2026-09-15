package com.checkbiz.backend.domain

import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "resenas")
class Resena(
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitud_id", nullable = false, unique = true)
    var solicitud: Solicitud? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "negocio_id", nullable = false)
    var negocio: Negocio? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    var cliente: Usuario? = null,

    @Column(nullable = false)
    var estrellas: Short = 5,

    @Column(columnDefinition = "text")
    var comentario: String? = null,

    @Column(name = "respuesta_negocio", columnDefinition = "text")
    var respuestaNegocio: String? = null,

    @Column(name = "respondida_en")
    var respondidaEn: OffsetDateTime? = null,

    @Column(name = "creado_en", nullable = false)
    var creadoEn: OffsetDateTime = OffsetDateTime.now(),
)

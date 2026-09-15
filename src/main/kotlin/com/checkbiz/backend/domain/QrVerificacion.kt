package com.checkbiz.backend.domain

import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "qr_verificacion")
class QrVerificacion(
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "negocio_id", nullable = false, unique = true)
    var negocio: Negocio? = null,

    @Column(nullable = false, unique = true, length = 40)
    var codigo: String = "",

    @Column(name = "escaneos_total", nullable = false)
    var escaneosTotal: Int = 0,

    @Column(name = "creado_en", nullable = false)
    var creadoEn: OffsetDateTime = OffsetDateTime.now(),
)

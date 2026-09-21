package com.checkbiz.backend.domain

import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "catalogo_items")
class CatalogoItem(
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "negocio_id", nullable = false)
    var negocio: Negocio? = null,

    @Column(nullable = false, length = 120)
    var nombre: String = "",

    @Column(name = "precio_referencial", precision = 10, scale = 2)
    var precioReferencial: BigDecimal? = null,

    @Column(name = "foto_url", columnDefinition = "text")
    var fotoUrl: String? = null,

    @Column(name = "nombre_en", length = 120)
    var nombreEn: String? = null,

    @Column(nullable = false)
    var orden: Short = 0,

    @Column(nullable = false)
    var activo: Boolean = true,

    @Column(name = "creado_en", nullable = false)
    var creadoEn: OffsetDateTime = OffsetDateTime.now(),
)

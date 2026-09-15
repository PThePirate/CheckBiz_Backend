package com.checkbiz.backend.domain

import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "negocios")
class Negocio(
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    var usuario: Usuario? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    var categoria: Categoria? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "universidad_id")
    var universidad: Universidad? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camara_id")
    var camara: Camara? = null,

    @Column(name = "nombre_comercial", nullable = false, length = 120)
    var nombreComercial: String = "",

    @Column(nullable = false, unique = true, length = 150)
    var slug: String = "",

    @Column(name = "descripcion_corta", length = 280)
    var descripcionCorta: String? = null,

    @Column(length = 80)
    var ciudad: String? = null,

    @Column(nullable = false, length = 15)
    var whatsapp: String = "",

    @Column(name = "foto_portada_url", columnDefinition = "text")
    var fotoPortadaUrl: String? = null,

    @Column(name = "logo_url", columnDefinition = "text")
    var logoUrl: String? = null,

    @Column(name = "video_presentacion_url", columnDefinition = "text")
    var videoPresentacionUrl: String? = null,

    @Column(name = "trust_score", nullable = false)
    var trustScore: Short = 0,

    @Column(name = "nivel_formalizacion", nullable = false, length = 20)
    var nivelFormalizacion: String = "semilla",

    @Column(name = "estado_publicacion", nullable = false, length = 20)
    var estadoPublicacion: String = "borrador",

    @Column(name = "creado_en", nullable = false)
    var creadoEn: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "actualizado_en", nullable = false)
    var actualizadoEn: OffsetDateTime = OffsetDateTime.now(),
)

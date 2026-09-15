package com.checkbiz.backend.domain

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "planes")
class Plan(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @Column(nullable = false, unique = true, length = 30)
    var nombre: String = "basico",

    @Column(name = "precio_mensual", nullable = false, precision = 6, scale = 2)
    var precioMensual: BigDecimal = BigDecimal.ZERO,

    @Column(name = "precio_semestral", nullable = false, precision = 6, scale = 2)
    var precioSemestral: BigDecimal = BigDecimal.ZERO,

    @Column(name = "limite_catalogo", nullable = false)
    var limiteCatalogo: Short = 3,

    @Column(name = "incluye_video", nullable = false)
    var incluyeVideo: Boolean = false,

    @Column(name = "incluye_analitica_avanzada", nullable = false)
    var incluyeAnaliticaAvanzada: Boolean = false,

    @Column(name = "incluye_multiusuario", nullable = false)
    var incluyeMultiusuario: Boolean = false,

    @Column(name = "incluye_traduccion", nullable = false)
    var incluyeTraduccion: Boolean = false,

    @Column(name = "incluye_certificado_pdf", nullable = false)
    var incluyeCertificadoPdf: Boolean = false,

    @Column(name = "incluye_whatsapp_business_api", nullable = false)
    var incluyeWhatsappBusinessApi: Boolean = false,
)

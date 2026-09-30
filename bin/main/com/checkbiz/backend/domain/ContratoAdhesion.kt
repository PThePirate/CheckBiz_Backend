package com.checkbiz.backend.domain

import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Firma del Contrato de Adhesión / Declaración Responsable.
 * Requisito legal (Ley de Comercio Electrónico): se guarda IP y fecha/hora
 * exactas del momento de la firma, no solo un booleano de aceptación.
 */
@Entity
@Table(name = "contratos_adhesion")
class ContratoAdhesion(
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

    @Column(name = "version_documento", nullable = false, length = 10)
    var versionDocumento: String = "v1",

    @Column(name = "ip_firma", nullable = false, length = 45)
    var ipFirma: String = "",

    @Column(name = "user_agent", columnDefinition = "text")
    var userAgent: String? = null,

    @Column(name = "firmado_en", nullable = false)
    var firmadoEn: OffsetDateTime = OffsetDateTime.now(),
)

package com.checkbiz.backend.domain

import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "otp_verificaciones")
class OtpVerificacion(
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    var usuario: Usuario? = null,

    @Column(nullable = false, length = 6)
    var codigo: String = "",

    @Column(nullable = false, length = 10)
    var canal: String = "sms",

    @Column(nullable = false)
    var intentos: Short = 0,

    @Column(nullable = false)
    var verificado: Boolean = false,

    @Column(name = "expira_en", nullable = false)
    var expiraEn: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "creado_en", nullable = false)
    var creadoEn: OffsetDateTime = OffsetDateTime.now(),
)

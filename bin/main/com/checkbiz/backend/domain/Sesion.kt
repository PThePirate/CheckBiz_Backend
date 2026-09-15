package com.checkbiz.backend.domain

import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Refresh tokens. No se usa todavía en los controladores actuales (la
 * autenticación es JWT stateless), pero la tabla queda lista para cuando
 * se implemente rotación/revocación de sesiones.
 */
@Entity
@Table(name = "sesiones")
class Sesion(
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(name = "usuario_id", columnDefinition = "uuid")
    var usuarioId: UUID? = null,

    @Column(name = "admin_id", columnDefinition = "uuid")
    var adminId: UUID? = null,

    @Column(name = "cuenta_institucional_id", columnDefinition = "uuid")
    var cuentaInstitucionalId: UUID? = null,

    @Column(name = "refresh_token_hash", nullable = false, unique = true, columnDefinition = "text")
    var refreshTokenHash: String = "",

    @Column(name = "user_agent", columnDefinition = "text")
    var userAgent: String? = null,

    @Column(length = 45)
    var ip: String? = null,

    @Column(name = "expira_en", nullable = false)
    var expiraEn: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "creado_en", nullable = false)
    var creadoEn: OffsetDateTime = OffsetDateTime.now(),
)

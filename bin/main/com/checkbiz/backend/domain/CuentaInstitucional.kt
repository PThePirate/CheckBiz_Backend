package com.checkbiz.backend.domain

import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "cuentas_institucionales")
class CuentaInstitucional(
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(nullable = false, length = 25)
    var tipo: String = "",

    @Column(name = "nombre_institucion", nullable = false, length = 150)
    var nombreInstitucion: String = "",

    @Column(nullable = false, unique = true, length = 150)
    var correo: String = "",

    @Column(name = "password_hash", nullable = false, columnDefinition = "text")
    var passwordHash: String = "",

    @Column(name = "logo_url", columnDefinition = "text")
    var logoUrl: String? = null,

    @Column(nullable = false)
    var activo: Boolean = true,

    @Column(name = "creado_en", nullable = false)
    var creadoEn: OffsetDateTime = OffsetDateTime.now(),
)

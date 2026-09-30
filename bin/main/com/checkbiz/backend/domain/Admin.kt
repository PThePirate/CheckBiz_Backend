package com.checkbiz.backend.domain

import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "admins")
class Admin(
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(nullable = false, length = 100)
    var nombre: String = "",

    @Column(nullable = false, unique = true, length = 150)
    var correo: String = "",

    @Column(name = "password_hash", nullable = false, columnDefinition = "text")
    var passwordHash: String = "",

    @Column(nullable = false, length = 20)
    var rol: String = "admin",

    @Column(nullable = false)
    var activo: Boolean = true,

    @Column(name = "creado_en", nullable = false)
    var creadoEn: OffsetDateTime = OffsetDateTime.now(),
)

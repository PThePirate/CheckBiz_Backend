package com.checkbiz.backend.domain

import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "veto_cedulas")
class VetoCedula(
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(nullable = false, unique = true, length = 10)
    var cedula: String = "",

    @Column(nullable = false, columnDefinition = "text")
    var motivo: String = "",

    @Column(name = "vetado_por", nullable = false, columnDefinition = "uuid")
    var vetadoPor: UUID? = null,

    @Column(name = "creado_en", nullable = false)
    var creadoEn: OffsetDateTime = OffsetDateTime.now(),
)

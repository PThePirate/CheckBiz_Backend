package com.checkbiz.backend.domain

import jakarta.persistence.*

@Entity
@Table(name = "universidades")
class Universidad(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @Column(nullable = false, length = 150)
    var nombre: String = "",

    @Column(name = "logo_url", columnDefinition = "text")
    var logoUrl: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_institucional_id")
    var cuenta: CuentaInstitucional? = null,

    @Column(nullable = false)
    var activa: Boolean = true,
)

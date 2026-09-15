package com.checkbiz.backend.domain

import jakarta.persistence.*

@Entity
@Table(name = "insignias")
class Insignia(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @Column(nullable = false, length = 60)
    var nombre: String = "",

    @Column(length = 200)
    var descripcion: String? = null,

    @Column(length = 40)
    var icono: String? = null,

    @Column(nullable = false, length = 20)
    var tipo: String = "negocio",
)

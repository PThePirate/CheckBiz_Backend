package com.checkbiz.backend.domain

import jakarta.persistence.*

@Entity
@Table(name = "categorias")
class Categoria(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,

    @Column(nullable = false, unique = true, length = 60)
    var nombre: String = "",

    @Column(length = 40)
    var icono: String? = null,

    @Column(nullable = false)
    var activa: Boolean = true,
)

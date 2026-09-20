package com.checkbiz.backend.domain

import jakarta.persistence.*
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Identidad ciudadana central. 1 cédula = 1 fila = 1 cuenta.
 * rolCliente y rolEmprendedor son capacidades acumulables sobre la misma
 * identidad — nunca se crean cuentas separadas para el mismo ciudadano.
 */
@Entity
@Table(name = "usuarios")
class Usuario(
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @Column(nullable = false, unique = true, length = 10)
    var cedula: String = "",

    @Column(name = "nombre_completo", nullable = false, length = 150)
    var nombreCompleto: String = "",

    @Column(nullable = false, unique = true, length = 150)
    var correo: String = "",

    @Column(nullable = false, length = 15)
    var telefono: String = "",

    @Column(nullable = false, length = 2)
    var pais: String = "EC",

    @Column(name = "password_hash", nullable = false, columnDefinition = "text")
    var passwordHash: String = "",

    @Column(name = "rol_cliente", nullable = false)
    var rolCliente: Boolean = true,

    @Column(name = "rol_emprendedor", nullable = false)
    var rolEmprendedor: Boolean = false,

    @Column(name = "kyc_layer", nullable = false)
    var kycLayer: Short = 1,

    @Column(name = "foto_verificacion_estado", nullable = false, length = 20)
    var fotoVerificacionEstado: String = "no_iniciada",

    @Column(name = "senescyt_sri_estado", nullable = false, length = 20)
    var senescytSriEstado: String = "no_verificado",

    @Column(name = "estado_cedula", nullable = false, length = 20)
    var estadoCedula: String = "activa",

    @Column(name = "foto_perfil_url")
    var fotoPerfilUrl: String? = null,

    @Column(name = "acepto_terminos", nullable = false)
    var aceptoTerminos: Boolean = false,

    @Column(name = "creado_en", nullable = false)
    var creadoEn: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "actualizado_en", nullable = false)
    var actualizadoEn: OffsetDateTime = OffsetDateTime.now(),
)

package com.checkbiz.backend.domain

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "admin_logs_auditoria")
class AdminLogAuditoria(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "admin_id", nullable = false, columnDefinition = "uuid")
    var adminId: UUID? = null,

    @Column(nullable = false, length = 60)
    var accion: String = "",

    @Column(name = "entidad_afectada", length = 60)
    var entidadAfectada: String? = null,

    @Column(name = "entidad_id", length = 60)
    var entidadId: String? = null,

    // Hibernate 6 mapea un String anotado con @JdbcTypeCode(SqlTypes.JSON)
    // directamente a jsonb — el valor es el JSON ya serializado como texto.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var detalle: String? = null,

    @Column(name = "creado_en", nullable = false)
    var creadoEn: OffsetDateTime = OffsetDateTime.now(),
)

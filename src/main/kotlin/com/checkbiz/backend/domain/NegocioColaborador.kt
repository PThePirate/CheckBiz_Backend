package com.checkbiz.backend.domain

import jakarta.persistence.*
import java.io.Serializable
import java.time.OffsetDateTime
import java.util.UUID

@Embeddable
data class NegocioColaboradorId(
    @Column(name = "negocio_id", columnDefinition = "uuid")
    var negocioId: UUID? = null,

    @Column(name = "usuario_id", columnDefinition = "uuid")
    var usuarioId: UUID? = null,
) : Serializable

/**
 * B9.1 (Elite) — multiusuario. Un colaborador opera el mismo negocio que su
 * dueño (editar perfil, catálogo, solicitudes) pero nunca puede cambiar el
 * plan, invitar/quitar a otros colaboradores ni eliminar la cuenta — eso
 * sigue siendo exclusivo del dueño (NegocioService lo valida aparte).
 */
@Entity
@Table(name = "negocio_colaboradores")
class NegocioColaborador(
    @EmbeddedId
    var id: NegocioColaboradorId = NegocioColaboradorId(),

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("negocioId")
    @JoinColumn(name = "negocio_id")
    var negocio: Negocio? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("usuarioId")
    @JoinColumn(name = "usuario_id")
    var usuario: Usuario? = null,

    @Column(name = "agregado_en", nullable = false)
    var agregadoEn: OffsetDateTime = OffsetDateTime.now(),
)

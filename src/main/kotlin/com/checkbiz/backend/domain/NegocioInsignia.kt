package com.checkbiz.backend.domain

import jakarta.persistence.*
import java.io.Serializable
import java.time.OffsetDateTime
import java.util.UUID

@Embeddable
data class NegocioInsigniaId(
    @Column(name = "negocio_id", columnDefinition = "uuid")
    var negocioId: UUID? = null,

    @Column(name = "insignia_id")
    var insigniaId: Int? = null,
) : Serializable

@Entity
@Table(name = "negocio_insignias")
class NegocioInsignia(
    @EmbeddedId
    var id: NegocioInsigniaId = NegocioInsigniaId(),

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("negocioId")
    @JoinColumn(name = "negocio_id")
    var negocio: Negocio? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("insigniaId")
    @JoinColumn(name = "insignia_id")
    var insignia: Insignia? = null,

    @Column(name = "obtenida_en", nullable = false)
    var obtenidaEn: OffsetDateTime = OffsetDateTime.now(),
)

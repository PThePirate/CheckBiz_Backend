package com.checkbiz.backend.service

import com.checkbiz.backend.domain.CatalogoItem
import com.checkbiz.backend.domain.Negocio
import com.checkbiz.backend.domain.Resena
import com.checkbiz.backend.dto.*
import com.checkbiz.backend.exception.AppException
import com.checkbiz.backend.repository.CategoriaRepository
import com.checkbiz.backend.repository.CatalogoItemRepository
import com.checkbiz.backend.repository.NegocioRepository
import com.checkbiz.backend.repository.ResenaRepository
import com.checkbiz.backend.repository.SolicitudRepository
import com.checkbiz.backend.repository.UsuarioRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.roundToInt

@Service
class NegocioService(
    private val negocioRepository: NegocioRepository,
    private val catalogoRepository: CatalogoItemRepository,
    private val usuarioRepository: UsuarioRepository,
    private val categoriaRepository: CategoriaRepository,
    private val resenaRepository: ResenaRepository,
    private val solicitudRepository: SolicitudRepository,
) {

    // ===================================================================
    // Negocio (B3)
    // ===================================================================

    @Transactional
    fun crear(usuarioId: UUID, req: CrearNegocioRequest): NegocioResponse {
        val usuario = usuarioRepository.findById(usuarioId).orElseThrow()

        if (!usuario.rolEmprendedor) {
            throw AppException(
                HttpStatus.FORBIDDEN, "SIN_ROL_EMPRENDEDOR",
                "Debes activar tu perfil de emprendedor antes de crear tu Mini Landing Page"
            )
        }
        if (negocioRepository.findByUsuarioId(usuarioId) != null) {
            throw AppException(HttpStatus.CONFLICT, "YA_TIENE_NEGOCIO", "Ya tienes un negocio creado")
        }

        val categoria = req.categoriaId?.let {
            categoriaRepository.findById(it).orElseThrow {
                AppException(HttpStatus.BAD_REQUEST, "CATEGORIA_INVALIDA", "La categoría seleccionada no existe")
            }
        }

        val negocio = negocioRepository.save(
            Negocio(
                usuario = usuario,
                categoria = categoria,
                nombreComercial = req.nombreComercial,
                slug = generarSlug(req.nombreComercial),
                descripcionCorta = req.descripcionCorta,
                ciudad = req.ciudad,
                whatsapp = req.whatsapp,
                estadoPublicacion = "borrador",
            )
        )
        negocio.trustScore = recalcularTrustScore(negocio.id!!)

        return negocio.aResponse(0)
    }

    // Sin transacción de lectura, aResponse() falla al tocar relaciones
    // LAZY del negocio (categoría, catálogo) porque open-in-view está
    // apagado y la sesión de Hibernate ya se cerró.
    @Transactional(readOnly = true)
    fun obtenerMiNegocio(usuarioId: UUID): NegocioResponse {
        val negocio = negocioRepository.findByUsuarioId(usuarioId)
            ?: throw AppException(HttpStatus.NOT_FOUND, "SIN_NEGOCIO", "Todavía no has creado tu negocio")
        val total = catalogoRepository.countByNegocioIdAndActivoTrue(negocio.id!!)
        return negocio.aResponse(total)
    }

    @Transactional
    fun actualizar(usuarioId: UUID, req: ActualizarNegocioRequest): NegocioResponse {
        val negocio = miNegocioOrThrow(usuarioId)

        val categoria = req.categoriaId?.let {
            categoriaRepository.findById(it).orElseThrow {
                AppException(HttpStatus.BAD_REQUEST, "CATEGORIA_INVALIDA", "La categoría seleccionada no existe")
            }
        }

        negocio.nombreComercial = req.nombreComercial
        negocio.categoria = categoria
        negocio.descripcionCorta = req.descripcionCorta
        negocio.ciudad = req.ciudad
        negocio.whatsapp = req.whatsapp
        req.fotoPortadaUrl?.let { negocio.fotoPortadaUrl = it }
        req.logoUrl?.let { negocio.logoUrl = it }
        negocio.actualizadoEn = OffsetDateTime.now()
        negocioRepository.save(negocio)

        val total = catalogoRepository.countByNegocioIdAndActivoTrue(negocio.id!!)
        return negocio.aResponse(total)
    }

    @Transactional
    fun cambiarPublicacion(usuarioId: UUID, publicar: Boolean): NegocioResponse {
        val negocio = miNegocioOrThrow(usuarioId)

        if (publicar) {
            if (negocio.whatsapp.isBlank() || negocio.nombreComercial.isBlank()) {
                throw AppException(
                    HttpStatus.BAD_REQUEST, "NEGOCIO_INCOMPLETO",
                    "Completa al menos el nombre y el WhatsApp antes de publicar"
                )
            }
        }

        negocio.estadoPublicacion = if (publicar) "publicado" else "borrador"
        negocio.actualizadoEn = OffsetDateTime.now()
        negocioRepository.save(negocio)

        val total = catalogoRepository.countByNegocioIdAndActivoTrue(negocio.id!!)
        return negocio.aResponse(total)
    }

    // ===================================================================
    // Catálogo (B4)
    // ===================================================================

    fun listarCatalogo(usuarioId: UUID): List<ItemCatalogoResponse> {
        val negocio = miNegocioOrThrow(usuarioId)
        return catalogoRepository.findByNegocioIdOrderByOrdenAsc(negocio.id!!).map { it.aResponse() }
    }

    @Transactional
    fun crearItem(usuarioId: UUID, req: CrearItemCatalogoRequest): ItemCatalogoResponse {
        val negocio = miNegocioOrThrow(usuarioId)
        val siguienteOrden = catalogoRepository.findByNegocioIdOrderByOrdenAsc(negocio.id!!).size

        val item = catalogoRepository.save(
            CatalogoItem(
                negocio = negocio,
                nombre = req.nombre,
                precioReferencial = req.precioReferencial,
                fotoUrl = req.fotoUrl,
                orden = siguienteOrden.toShort(),
                activo = true,
            )
        )
        return item.aResponse()
    }

    @Transactional
    fun actualizarItem(usuarioId: UUID, itemId: UUID, req: ActualizarItemCatalogoRequest): ItemCatalogoResponse {
        val item = itemDePropietarioOrThrow(usuarioId, itemId)
        item.nombre = req.nombre
        item.precioReferencial = req.precioReferencial
        item.fotoUrl = req.fotoUrl
        item.activo = req.activo
        catalogoRepository.save(item)
        return item.aResponse()
    }

    @Transactional
    fun eliminarItem(usuarioId: UUID, itemId: UUID) {
        val item = itemDePropietarioOrThrow(usuarioId, itemId)
        catalogoRepository.delete(item)
    }

    fun categoriasDisponibles(): List<CategoriaResumenResponse> =
        categoriaRepository.findAllByOrderByNombreAsc()
            .filter { it.activa }
            .map { CategoriaResumenResponse(it.id!!, it.nombre, it.icono) }

    // ===================================================================
    // Perfil público (A6 — Mini Landing Page)
    // ===================================================================
    @Transactional(readOnly = true)
    fun obtenerPublicoPorSlug(slug: String): NegocioPublicoResponse {
        val negocio = negocioRepository.findBySlugAndEstadoPublicacion(slug, "publicado")
            ?: throw AppException(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "Este negocio no existe o no está publicado")

        val propietario = negocio.usuario!!
        val capas = listOf(
            CapaVerificacionResponse("Estructura", propietario.kycLayer >= 1),
            CapaVerificacionResponse("Correo verificado", propietario.kycLayer >= 2),
            CapaVerificacionResponse("Foto revisada", propietario.fotoVerificacionEstado == "aprobada"),
            CapaVerificacionResponse("SENESCYT/SRI", propietario.senescytSriEstado == "verificado"),
        )

        val catalogo = catalogoRepository.findByNegocioIdOrderByOrdenAsc(negocio.id!!)
            .filter { it.activo }
            .map { it.aResponse() }

        val resenas = resenaRepository.findByNegocioIdOrderByCreadoEnDesc(negocio.id!!).map { it.aDetalleResponse() }

        return NegocioPublicoResponse(
            nombreComercial = negocio.nombreComercial, slug = negocio.slug,
            descripcionCorta = negocio.descripcionCorta, ciudad = negocio.ciudad, whatsapp = negocio.whatsapp,
            fotoPortadaUrl = negocio.fotoPortadaUrl, logoUrl = negocio.logoUrl,
            videoPresentacionUrl = negocio.videoPresentacionUrl,
            categoria = negocio.categoria?.let { CategoriaResumenResponse(it.id!!, it.nombre, it.icono) },
            trustScore = negocio.trustScore, nivelFormalizacion = negocio.nivelFormalizacion,
            capasVerificacion = capas, catalogo = catalogo,
            totalResenas = resenaRepository.countByNegocioId(negocio.id!!),
            promedioResenas = resenaRepository.promedioEstrellas(negocio.id!!),
            resenas = resenas,
            creadoEn = negocio.creadoEn,
        )
    }

    // ===================================================================
    // Búsqueda pública (A4/A5)
    // ===================================================================
    fun buscarPublicados(categoriaId: Int?, ciudad: String?, nivel: String?, texto: String?): List<NegocioResumenPublicoResponse> =
        negocioRepository.buscarPublicados(categoriaId, ciudad, nivel, texto).map { n ->
            NegocioResumenPublicoResponse(
                nombreComercial = n.nombreComercial, slug = n.slug,
                descripcionCorta = n.descripcionCorta, ciudad = n.ciudad,
                fotoPortadaUrl = n.fotoPortadaUrl, logoUrl = n.logoUrl,
                categoria = n.categoria?.let { CategoriaResumenResponse(it.id!!, it.nombre, it.icono) },
                trustScore = n.trustScore, nivelFormalizacion = n.nivelFormalizacion,
            )
        }

    fun ciudadesDisponibles(): List<String> = negocioRepository.ciudadesDisponibles()

    // ===================================================================
    // Bandeja de solicitudes (B5)
    // ===================================================================
    @Transactional(readOnly = true)
    fun misSolicitudesRecibidas(usuarioId: UUID): List<SolicitudRecibidaResponse> {
        val negocio = miNegocioOrThrow(usuarioId)
        return solicitudRepository.findByNegocioIdOrderByCreadoEnDesc(negocio.id!!).map { s ->
            val cliente = s.cliente!!
            SolicitudRecibidaResponse(
                id = s.id!!,
                cliente = ClienteResumenResponse(cliente.nombreCompleto, cliente.telefono),
                descripcion = s.descripcion, fechaEstimada = s.fechaEstimada, estado = s.estado,
                creadoEn = s.creadoEn, confirmadaEn = s.confirmadaEn,
            )
        }
    }

    @Transactional
    fun actualizarEstadoSolicitud(usuarioId: UUID, solicitudId: UUID, nuevoEstado: String): SolicitudRecibidaResponse {
        val negocio = miNegocioOrThrow(usuarioId)
        val solicitud = solicitudRepository.findById(solicitudId)
            .orElseThrow { AppException(HttpStatus.NOT_FOUND, "NO_ENCONTRADA", "Solicitud no encontrada") }

        if (solicitud.negocio?.id != negocio.id) {
            throw AppException(HttpStatus.FORBIDDEN, "NO_AUTORIZADO", "Esta solicitud no pertenece a tu negocio")
        }
        if (solicitud.estado == "confirmada") {
            throw AppException(
                HttpStatus.CONFLICT, "YA_CONFIRMADA",
                "El cliente ya confirmó esta solicitud — no puedes cambiar su estado"
            )
        }

        solicitud.estado = nuevoEstado
        solicitud.actualizadoEn = OffsetDateTime.now()
        solicitudRepository.save(solicitud)

        val cliente = solicitud.cliente!!
        return SolicitudRecibidaResponse(
            id = solicitud.id!!,
            cliente = ClienteResumenResponse(cliente.nombreCompleto, cliente.telefono),
            descripcion = solicitud.descripcion, fechaEstimada = solicitud.fechaEstimada, estado = solicitud.estado,
            creadoEn = solicitud.creadoEn, confirmadaEn = solicitud.confirmadaEn,
        )
    }

    // ===================================================================
    // Reputación y Trust Score (B6)
    // ===================================================================

    /**
     * Recalcula y persiste el Trust Score de un negocio a partir de señales
     * reales (nunca un número inventado). Se llama cada vez que cambia algo
     * que debería afectarlo: una reseña nueva, o una solicitud confirmada.
     *
     * Fórmula (0-100), cuatro componentes con peso fijo:
     *  - Identidad verificada  (hasta 40 pts) — cuántas de las 4 capas
     *    públicas del dueño están cumplidas (Estructura/Teléfono/Foto/SRI).
     *  - Calidad de servicio   (hasta 30 pts) — promedio de estrellas de
     *    sus reseñas reales, normalizado sobre 5. Si no tiene reseñas
     *    todavía, este componente aporta 0 (no se inventa un promedio).
     *  - Cumplimiento          (hasta 20 pts) — % de solicitudes que
     *    terminaron confirmadas por el cliente, sobre el total recibidas.
     *  - Nivel de formalización (hasta 10 pts) — semilla=0, asesoría=5,
     *    formalizado=10.
     */
    @Transactional
    fun recalcularTrustScore(negocioId: UUID): Short {
        val negocio = negocioRepository.findById(negocioId).orElseThrow {
            AppException(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "Negocio no encontrado")
        }
        val propietario = negocio.usuario!!

        val capasCumplidas = listOf(
            propietario.kycLayer >= 1,
            propietario.kycLayer >= 2,
            propietario.fotoVerificacionEstado == "aprobada",
            propietario.senescytSriEstado == "verificado",
        ).count { it }
        val puntosIdentidad = 40.0 * capasCumplidas / 4.0

        val totalResenas = resenaRepository.countByNegocioId(negocioId)
        val puntosResenas = if (totalResenas > 0) {
            30.0 * (resenaRepository.promedioEstrellas(negocioId) / 5.0)
        } else 0.0

        val totalSolicitudes = solicitudRepository.countByNegocioId(negocioId)
        val puntosCumplimiento = if (totalSolicitudes > 0) {
            val confirmadas = solicitudRepository.countByNegocioIdAndEstado(negocioId, "confirmada")
            20.0 * (confirmadas.toDouble() / totalSolicitudes)
        } else 0.0

        val puntosNivel = when (negocio.nivelFormalizacion) {
            "formalizado" -> 10.0
            "asesoria" -> 5.0
            else -> 0.0
        }

        val total = (puntosIdentidad + puntosResenas + puntosCumplimiento + puntosNivel)
            .roundToInt().coerceIn(0, 100).toShort()

        negocio.trustScore = total
        negocioRepository.save(negocio)
        return total
    }

    fun obtenerReputacion(usuarioId: UUID): ReputacionResponse {
        val negocio = miNegocioOrThrow(usuarioId)
        val totalResenas = resenaRepository.countByNegocioId(negocio.id!!)
        val totalSolicitudes = solicitudRepository.countByNegocioId(negocio.id!!)
        val confirmadas = solicitudRepository.countByNegocioIdAndEstado(negocio.id!!, "confirmada")

        return ReputacionResponse(
            trustScore = negocio.trustScore,
            nivelFormalizacion = negocio.nivelFormalizacion,
            totalResenas = totalResenas,
            promedioResenas = if (totalResenas > 0) resenaRepository.promedioEstrellas(negocio.id!!) else 0.0,
            totalSolicitudes = totalSolicitudes,
            solicitudesConfirmadas = confirmadas,
            tasaConfirmacion = if (totalSolicitudes > 0) confirmadas.toDouble() / totalSolicitudes else 0.0,
            antiguedadDias = ChronoUnit.DAYS.between(negocio.creadoEn, OffsetDateTime.now()),
        )
    }

    fun listarMisResenas(usuarioId: UUID): List<ResenaDetalleResponse> {
        val negocio = miNegocioOrThrow(usuarioId)
        return resenaRepository.findByNegocioIdOrderByCreadoEnDesc(negocio.id!!).map { it.aDetalleResponse() }
    }

    @Transactional
    fun responderResena(usuarioId: UUID, resenaId: UUID, respuesta: String): ResenaDetalleResponse {
        val negocio = miNegocioOrThrow(usuarioId)
        val resena = resenaRepository.findById(resenaId)
            .orElseThrow { AppException(HttpStatus.NOT_FOUND, "NO_ENCONTRADA", "Reseña no encontrada") }

        if (resena.negocio?.id != negocio.id) {
            throw AppException(HttpStatus.FORBIDDEN, "NO_AUTORIZADO", "Esta reseña no pertenece a tu negocio")
        }
        if (resena.respuestaNegocio != null) {
            throw AppException(HttpStatus.CONFLICT, "YA_RESPONDIDA", "Ya respondiste esta reseña")
        }

        resena.respuestaNegocio = respuesta
        resena.respondidaEn = OffsetDateTime.now()
        resenaRepository.save(resena)
        return resena.aDetalleResponse()
    }

    private fun Resena.aDetalleResponse() = ResenaDetalleResponse(
        id = id!!, clienteNombre = cliente?.nombreCompleto ?: "Cliente",
        estrellas = estrellas, comentario = comentario,
        respuestaNegocio = respuestaNegocio, respondidaEn = respondidaEn, creadoEn = creadoEn,
    )

    // ===================================================================
    private fun miNegocioOrThrow(usuarioId: UUID): Negocio =
        negocioRepository.findByUsuarioId(usuarioId)
            ?: throw AppException(HttpStatus.NOT_FOUND, "SIN_NEGOCIO", "Todavía no has creado tu negocio")

    /** Verifica que el ítem exista Y pertenezca al negocio del usuario autenticado. */
    private fun itemDePropietarioOrThrow(usuarioId: UUID, itemId: UUID): CatalogoItem {
        val negocio = miNegocioOrThrow(usuarioId)
        val item = catalogoRepository.findById(itemId)
            .orElseThrow { AppException(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "Ítem de catálogo no encontrado") }
        if (item.negocio?.id != negocio.id) {
            throw AppException(HttpStatus.FORBIDDEN, "NO_AUTORIZADO", "Este ítem no pertenece a tu negocio")
        }
        return item
    }

    private fun generarSlug(nombre: String): String {
        val base = nombre.lowercase()
            .replace(Regex("[áàäâ]"), "a").replace(Regex("[éèëê]"), "e")
            .replace(Regex("[íìïî]"), "i").replace(Regex("[óòöô]"), "o")
            .replace(Regex("[úùüû]"), "u").replace("ñ", "n")
            .replace(Regex("[^a-z0-9]+"), "-").trim('-')
        var slug = base.ifBlank { "negocio" }
        var intento = 1
        while (negocioRepository.existsBySlug(slug)) {
            intento++
            slug = "$base-$intento"
        }
        return slug
    }

    private fun Negocio.aResponse(totalCatalogo: Long) = NegocioResponse(
        id = id!!, nombreComercial = nombreComercial, slug = slug,
        descripcionCorta = descripcionCorta, ciudad = ciudad, whatsapp = whatsapp,
        fotoPortadaUrl = fotoPortadaUrl, logoUrl = logoUrl,
        categoria = categoria?.let { CategoriaResumenResponse(it.id!!, it.nombre, it.icono) },
        trustScore = trustScore, nivelFormalizacion = nivelFormalizacion,
        estadoPublicacion = estadoPublicacion, totalCatalogo = totalCatalogo,
        creadoEn = creadoEn, actualizadoEn = actualizadoEn,
    )

    private fun CatalogoItem.aResponse() = ItemCatalogoResponse(
        id = id!!, nombre = nombre, precioReferencial = precioReferencial,
        fotoUrl = fotoUrl, orden = orden, activo = activo, creadoEn = creadoEn,
    )
}
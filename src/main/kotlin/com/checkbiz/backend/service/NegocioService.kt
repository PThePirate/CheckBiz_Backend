package com.checkbiz.backend.service

import com.checkbiz.backend.domain.AnaliticaEvento
import com.checkbiz.backend.domain.CatalogoItem
import com.checkbiz.backend.domain.Negocio
import com.checkbiz.backend.domain.NegocioColaborador
import com.checkbiz.backend.domain.NegocioColaboradorId
import com.checkbiz.backend.domain.NegocioInsignia
import com.checkbiz.backend.domain.NegocioInsigniaId
import com.checkbiz.backend.domain.Notificacion
import com.checkbiz.backend.domain.Plan
import com.checkbiz.backend.domain.QrVerificacion
import com.checkbiz.backend.domain.Resena
import com.checkbiz.backend.domain.RutaFormalizacion
import com.checkbiz.backend.domain.Suscripcion
import com.checkbiz.backend.dto.*
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.checkbiz.backend.exception.AppException
import com.checkbiz.backend.repository.AlumniVerificacionRepository
import com.checkbiz.backend.repository.AnaliticaEventoRepository
import com.checkbiz.backend.repository.CategoriaRepository
import com.checkbiz.backend.repository.CatalogoItemRepository
import com.checkbiz.backend.repository.InsigniaRepository
import com.checkbiz.backend.repository.NegocioColaboradorRepository
import com.checkbiz.backend.repository.NegocioInsigniaRepository
import com.checkbiz.backend.repository.NegocioRepository
import com.checkbiz.backend.repository.NotificacionRepository
import com.checkbiz.backend.repository.PlanRepository
import com.checkbiz.backend.repository.QrVerificacionRepository
import com.checkbiz.backend.repository.ResenaRepository
import com.checkbiz.backend.repository.RutaFormalizacionRepository
import com.checkbiz.backend.repository.SolicitudRepository
import com.checkbiz.backend.repository.SuscripcionRepository
import com.checkbiz.backend.repository.UsuarioRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
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
    private val qrRepository: QrVerificacionRepository,
    private val analiticaRepository: AnaliticaEventoRepository,
    private val rutaRepository: RutaFormalizacionRepository,
    private val planRepository: PlanRepository,
    private val suscripcionRepository: SuscripcionRepository,
    private val notificacionRepository: NotificacionRepository,
    private val insigniaRepository: InsigniaRepository,
    private val negocioInsigniaRepository: NegocioInsigniaRepository,
    private val alumniVerificacionRepository: AlumniVerificacionRepository,
    private val negocioColaboradorRepository: NegocioColaboradorRepository,
    private val objectMapper: ObjectMapper,
) {

    companion object {
        private const val PLAN_GRATUITO = "basico"
    }

    // ===================================================================
    // Negocio (B3)
    // ===================================================================

    @Transactional
    fun crear(usuarioId: UUID, req: CrearNegocioRequest, prepararSuscripcion: Boolean = false): NegocioResponse {
        if (!prepararSuscripcion) throw AppException(HttpStatus.PAYMENT_REQUIRED, "PLAN_REQUERIDO", "Selecciona un plan para crear tu Mini Landing Page")
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
                slogan = req.slogan,
                ciudad = req.ciudad,
                whatsapp = req.whatsapp,
                estadoPublicacion = "borrador",
            )
        )
        negocio.trustScore = recalcularTrustScore(negocio.id!!)

        val planGratuito = planRepository.findByNombre(PLAN_GRATUITO)
            ?: throw AppException(HttpStatus.INTERNAL_SERVER_ERROR, "PLAN_BASICO_FALTANTE", "No se encontró el plan básico")
        suscripcionRepository.save(
            Suscripcion(negocio = negocio, plan = planGratuito, ciclo = "mensual", estado = "activa")
        )

        return negocio.aResponse(0, usuarioId)
    }

    // Sin transacción de lectura, aResponse() falla al tocar relaciones
    // LAZY del negocio (categoría, catálogo) porque open-in-view está
    // apagado y la sesión de Hibernate ya se cerró.
    @Transactional(readOnly = true)
    fun obtenerMiNegocio(usuarioId: UUID): NegocioResponse {
        val negocio = miNegocioOrThrow(usuarioId)
        val total = catalogoRepository.countByNegocioIdAndActivoTrue(negocio.id!!)
        return negocio.aResponse(total, usuarioId)
    }

    @Transactional
    fun actualizar(usuarioId: UUID, req: ActualizarNegocioRequest): NegocioResponse {
        val negocio = miNegocioOrThrow(usuarioId)
        exigirPlanLanding(negocio)

        val categoria = req.categoriaId?.let {
            categoriaRepository.findById(it).orElseThrow {
                AppException(HttpStatus.BAD_REQUEST, "CATEGORIA_INVALIDA", "La categoría seleccionada no existe")
            }
        }

        negocio.nombreComercial = req.nombreComercial
        negocio.categoria = categoria
        negocio.descripcionCorta = req.descripcionCorta
        negocio.slogan = req.slogan
        negocio.ciudad = req.ciudad
        negocio.whatsapp = req.whatsapp
        req.fotoPortadaUrl?.let { negocio.fotoPortadaUrl = it }
        req.logoUrl?.let { negocio.logoUrl = it }

        if (!req.videoPresentacionUrl.isNullOrBlank() && !planDe(negocio).incluyeVideo) {
            throw AppException(
                HttpStatus.FORBIDDEN, "PLAN_NO_INCLUYE_VIDEO",
                "Tu plan actual no incluye video de presentación. Mejora tu plan para agregarlo."
            )
        }
        if (!req.videoPresentacionUrl.isNullOrBlank() && req.videoPresentacionUrl != negocio.videoPresentacionUrl && !req.videoPresentacionUrl.startsWith("/api/negocios/videos/")) {
            throw AppException(HttpStatus.BAD_REQUEST, "VIDEO_INVALIDO", "Sube un video MP4 de hasta 45 segundos")
        }
        req.videoPresentacionUrl?.let { negocio.videoPresentacionUrl = it.ifBlank { null } }

        verificarCupos(mediosDe(negocio))

        negocio.actualizadoEn = OffsetDateTime.now()
        negocioRepository.save(negocio)

        val total = catalogoRepository.countByNegocioIdAndActivoTrue(negocio.id!!)
        return negocio.aResponse(total, usuarioId)
    }

    private fun bloquesDe(negocio: Negocio): List<LandingBloqueDto> = try {
        objectMapper.readValue(negocio.landingBloques, object : TypeReference<List<LandingBloqueDto>>() {})
    } catch (_: Exception) { emptyList() }

    private fun mediosDe(negocio: Negocio, bloques: List<LandingBloqueDto> = bloquesDe(negocio), logo: String? = negocio.logoUrl, portada: String? = negocio.fotoPortadaUrl, fotoExtra: String? = null, videoExtra: String? = null): MediosPlanResponse {
        val fotos = mutableSetOf<String>()
        listOf(logo, portada, fotoExtra).filterNotNull().filter(String::isNotBlank).forEach(fotos::add)
        catalogoRepository.findByNegocioIdOrderByOrdenAsc(negocio.id!!).mapNotNull { it.fotoUrl }.filter(String::isNotBlank).forEach(fotos::add)
        bloques.filter { it.tipo == "imagen" }.map { it.url }.filter(String::isNotBlank).forEach(fotos::add)
        bloques.filter { it.tipo in setOf("galeria", "mosaico", "antesdespues") }.flatMap { it.items }.filter(String::isNotBlank).forEach(fotos::add)
        val videos = (bloques.filter { it.tipo == "video" }.map { it.url } + listOfNotNull(negocio.videoPresentacionUrl, videoExtra)).filter(String::isNotBlank).toSet()
        val plus = planDe(negocio).nombre == "elite"
        return MediosPlanResponse(fotos.size, if (plus) 50 else 30, videos.size, if (plus) 10 else 5)
    }

    private fun verificarCupos(medios: MediosPlanResponse) {
        if (medios.fotosUsadas > medios.fotosPermitidas) throw AppException(HttpStatus.FORBIDDEN, "LIMITE_FOTOS", "Tu plan permite hasta ${medios.fotosPermitidas} fotos")
        if (medios.videosUsados > medios.videosPermitidos) throw AppException(HttpStatus.FORBIDDEN, "LIMITE_VIDEOS", "Tu plan permite hasta ${medios.videosPermitidos} videos")
    }

    fun obtenerMediosPlan(usuarioId: UUID): MediosPlanResponse = mediosDe(miNegocioOrThrow(usuarioId))

    fun prepararNuevoMedio(usuarioId: UUID, video: Boolean) {
        val negocio = miNegocioOrThrow(usuarioId)
        exigirPlanLanding(negocio)
        val uso = mediosDe(negocio)
        if (video && uso.videosUsados >= uso.videosPermitidos) throw AppException(HttpStatus.FORBIDDEN, "LIMITE_VIDEOS", "Tu plan permite hasta ${uso.videosPermitidos} videos")
        if (!video && uso.fotosUsadas >= uso.fotosPermitidas) throw AppException(HttpStatus.FORBIDDEN, "LIMITE_FOTOS", "Tu plan permite hasta ${uso.fotosPermitidas} fotos")
    }

    @Transactional
    fun guardarLandingBloques(usuarioId: UUID, req: GuardarLandingBloquesRequest): NegocioResponse {
        val negocio = miNegocioOrThrow(usuarioId)
        exigirPlanLanding(negocio)
        val gratis = setOf("titulo", "texto", "imagen", "catalogo", "confianza", "contacto", "tema")
        val emprende = setOf("lista", "cita", "separador", "espaciador", "resenas", "subtitulo", "destacado", "pasos", "servicios", "horario", "ubicacion", "precios", "garantias", "equipo", "aviso")
        val escala = setOf("galeria", "columnas", "acordeon", "pestanas", "comparativa", "cronologia", "indicadores", "mosaico", "antesdespues", "ficha", "proceso", "recursos", "testimonio", "agenda")
        val video = setOf("video")
        if (req.bloques.size > 60 || req.bloques.any { it.tipo !in gratis + emprende + escala + video || it.id.length > 80 || it.titulo.length > 160 || it.texto.length > 1500 || it.url.length > 500 || it.items.size > 12 || it.items.any { item -> item.length > 300 } }) {
            throw AppException(HttpStatus.BAD_REQUEST, "BLOQUES_INVALIDOS", "Revisa el contenido y el número de bloques de la página")
        }
        if (planDe(negocio).nombre == PLAN_GRATUITO && req.bloques.any { it.tipo in emprende + escala + video }) {
            throw AppException(HttpStatus.FORBIDDEN, "PLAN_NO_INCLUYE_BLOQUE", "Selecciona un plan Básico o Plus para usar estos bloques")
        }
        if (!planDe(negocio).incluyeAnaliticaAvanzada && req.bloques.any { it.tipo in escala }) {
            throw AppException(HttpStatus.FORBIDDEN, "PLAN_NO_INCLUYE_BLOQUE", "Los bloques avanzados están disponibles en el plan Plus")
        }
        fun urlPermitida(url: String) = url.isBlank() || url.startsWith("https://") || url.startsWith("/api/negocios/imagenes/") || url.startsWith("/api/negocios/videos/")
        if (req.bloques.any { it.tono !in setOf("claro", "tinte", "oscuro") || !urlPermitida(it.url) || (it.tipo in setOf("galeria", "mosaico", "antesdespues", "recursos") && it.items.any { url -> !urlPermitida(url) }) || (it.tipo == "antesdespues" && it.items.size > 2) }) {
            throw AppException(HttpStatus.BAD_REQUEST, "URL_INVALIDA", "Usa enlaces HTTPS o imágenes subidas a CheckBiz")
        }
        val colorValido = Regex("^#[0-9a-fA-F]{6}$")
        if (req.bloques.count { it.tipo == "tema" } > 1 || req.bloques.any { bloque ->
                listOf(bloque.colorFondo, bloque.colorTitulo, bloque.colorTexto, bloque.colorAcento).any { color -> color.isNotBlank() && !colorValido.matches(color) }
        }) {
            throw AppException(HttpStatus.BAD_REQUEST, "COLOR_INVALIDO", "Elige colores válidos para la página")
        }
        val videosAnteriores = bloquesDe(negocio).filter { it.tipo == "video" }.map { it.url }.toSet()
        if (req.bloques.any { it.tipo == "video" && it.url.isNotBlank() && !it.url.startsWith("/api/negocios/videos/") && it.url !in videosAnteriores }) {
            throw AppException(HttpStatus.BAD_REQUEST, "VIDEO_INVALIDO", "Sube un video MP4 de hasta 45 segundos")
        }
        verificarCupos(mediosDe(negocio, bloques = req.bloques))
        negocio.landingBloques = objectMapper.writeValueAsString(req.bloques)
        negocio.actualizadoEn = OffsetDateTime.now()
        negocioRepository.save(negocio)
        return negocio.aResponse(catalogoRepository.countByNegocioIdAndActivoTrue(negocio.id!!), usuarioId)
    }

    @Transactional
    fun actualizarLogo(usuarioId: UUID, logoUrl: String): NegocioResponse {
        val negocio = miNegocioOrThrow(usuarioId)
        exigirPlanLanding(negocio)
        negocio.logoUrl = logoUrl
        verificarCupos(mediosDe(negocio))
        negocio.actualizadoEn = OffsetDateTime.now()
        negocioRepository.save(negocio)

        val total = catalogoRepository.countByNegocioIdAndActivoTrue(negocio.id!!)
        return negocio.aResponse(total, usuarioId)
    }

    @Transactional
    fun actualizarPortada(usuarioId: UUID, fotoPortadaUrl: String): NegocioResponse {
        val negocio = miNegocioOrThrow(usuarioId)
        exigirPlanLanding(negocio)
        negocio.fotoPortadaUrl = fotoPortadaUrl
        verificarCupos(mediosDe(negocio))
        negocio.actualizadoEn = OffsetDateTime.now()
        negocioRepository.save(negocio)

        val total = catalogoRepository.countByNegocioIdAndActivoTrue(negocio.id!!)
        return negocio.aResponse(total, usuarioId)
    }

    @Transactional
    fun cambiarPublicacion(usuarioId: UUID, publicar: Boolean): NegocioResponse {
        val negocio = miNegocioOrThrow(usuarioId)
        exigirPlanLanding(negocio)

        if (publicar) {
            if (negocio.nombreComercial.isBlank()) {
                throw AppException(
                    HttpStatus.BAD_REQUEST, "NEGOCIO_INCOMPLETO",
                    "Completa el nombre comercial antes de publicar"
                )
            }
        }

        negocio.estadoPublicacion = if (publicar) "publicado" else "borrador"
        negocio.actualizadoEn = OffsetDateTime.now()
        negocioRepository.save(negocio)

        val total = catalogoRepository.countByNegocioIdAndActivoTrue(negocio.id!!)
        return negocio.aResponse(total, usuarioId)
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

        val actuales = catalogoRepository.countByNegocioIdAndActivoTrue(negocio.id!!)
        val limite = planDe(negocio).limiteCatalogo
        if (actuales >= limite) {
            throw AppException(
                HttpStatus.FORBIDDEN, "LIMITE_CATALOGO_ALCANZADO",
                "Tu plan actual permite hasta $limite ítems en el catálogo. Mejora tu plan para agregar más."
            )
        }

        if (!req.nombreEn.isNullOrBlank() && !planDe(negocio).incluyeTraduccion) {
            throw AppException(
                HttpStatus.FORBIDDEN, "PLAN_NO_INCLUYE_TRADUCCION",
                "Tu plan actual no incluye traducción de catálogo. Mejora tu plan para agregarla."
            )
        }

        val siguienteOrden = catalogoRepository.findByNegocioIdOrderByOrdenAsc(negocio.id!!).size

        val item = catalogoRepository.save(
            CatalogoItem(
                negocio = negocio,
                nombre = req.nombre,
                precioReferencial = req.precioReferencial,
                fotoUrl = req.fotoUrl,
                nombreEn = req.nombreEn?.ifBlank { null },
                orden = siguienteOrden.toShort(),
                activo = true,
            )
        )
        verificarCupos(mediosDe(negocio))
        actualizarInsignias(negocio.id!!)
        return item.aResponse()
    }

    @Transactional
    fun actualizarItem(usuarioId: UUID, itemId: UUID, req: ActualizarItemCatalogoRequest): ItemCatalogoResponse {
        val item = itemDePropietarioOrThrow(usuarioId, itemId)
        val negocio = item.negocio!!
        val plan = planDe(negocio)

        if (!req.nombreEn.isNullOrBlank() && !plan.incluyeTraduccion) {
            throw AppException(
                HttpStatus.FORBIDDEN, "PLAN_NO_INCLUYE_TRADUCCION",
                "Tu plan actual no incluye traducción de catálogo. Mejora tu plan para agregarla."
            )
        }

        if (!item.activo && req.activo && catalogoRepository.countByNegocioIdAndActivoTrue(negocio.id!!) >= plan.limiteCatalogo) {
            throw AppException(HttpStatus.FORBIDDEN, "LIMITE_CATALOGO_ALCANZADO", "Tu plan actual permite hasta ${plan.limiteCatalogo} ítems disponibles en el catálogo")
        }

        item.nombre = req.nombre
        item.precioReferencial = req.precioReferencial
        item.fotoUrl = req.fotoUrl
        item.activo = req.activo
        item.nombreEn = req.nombreEn?.ifBlank { null }
        verificarCupos(mediosDe(item.negocio!!))
        catalogoRepository.save(item)
        actualizarInsignias(item.negocio!!.id!!)
        return item.aResponse()
    }

    @Transactional
    fun eliminarItem(usuarioId: UUID, itemId: UUID) {
        val item = itemDePropietarioOrThrow(usuarioId, itemId)
        catalogoRepository.delete(item)
    }

    @Transactional
    fun actualizarFotoItem(usuarioId: UUID, itemId: UUID, fotoUrl: String): ItemCatalogoResponse {
        val item = itemDePropietarioOrThrow(usuarioId, itemId)
        item.fotoUrl = fotoUrl
        verificarCupos(mediosDe(item.negocio!!))
        catalogoRepository.save(item)
        return item.aResponse()
    }

    fun categoriasDisponibles(): List<CategoriaResumenResponse> =
        categoriaRepository.findAllByOrderByNombreAsc()
            .filter { it.activa }
            .map { CategoriaResumenResponse(it.id!!, it.nombre, it.icono) }

    // ===================================================================
    // Perfil público (A6 — Mini Landing Page)
    // ===================================================================
    // Ya no es readOnly: cada carga real de la Mini Landing deja un evento
    // "visita_validada" (B7) — la única fuente de verdad de "visitas al
    // perfil" del panel de analítica es esta misma llamada, nunca un
    // contador aparte que se pueda desincronizar.
    @Transactional
    fun obtenerPublicoPorSlug(slug: String, visitanteId: UUID? = null): NegocioPublicoResponse {
        val negocio = negocioRepository.findBySlugAndEstadoPublicacion(slug, "publicado")
            ?: throw AppException(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "Este negocio no existe o no está publicado")

        if (visitanteId != null && visitanteId != negocio.usuario?.id) { analiticaRepository.registrarVisitaUnica(negocio.id!!, visitanteId) }

        val propietario = negocio.usuario!!
        val capas = listOf(
            CapaVerificacionResponse("Estructura", propietario.kycLayer >= 1),
            CapaVerificacionResponse("Correo verificado", propietario.kycLayer >= 2),
            CapaVerificacionResponse("Cédula revisada", propietario.fotoVerificacionEstado == "aprobada"),
            CapaVerificacionResponse("SENESCYT/SRI", propietario.senescytSriEstado == "verificado"),
        )

        val permiteTraduccion = planDe(negocio).incluyeTraduccion
        val catalogo = catalogoRepository.findByNegocioIdOrderByOrdenAsc(negocio.id!!)
            .map { item -> item.aResponse().let { if (permiteTraduccion) it else it.copy(nombreEn = null) } }

        val resenas = resenaRepository.findByNegocioIdOrderByCreadoEnDesc(negocio.id!!).map { it.aDetalleResponse() }

        val insignias = negocioInsigniaRepository.findByNegocioIdOrderByObtenidaEnDesc(negocio.id!!).map { it.aInsigniaResponse() }

        return NegocioPublicoResponse(
            nombreComercial = negocio.nombreComercial, slug = negocio.slug,
            descripcionCorta = negocio.descripcionCorta, slogan = negocio.slogan,
            emprendedorNombre = propietario.nombreUsuario,
            ciudad = negocio.ciudad,
            fotoPortadaUrl = negocio.fotoPortadaUrl, logoUrl = negocio.logoUrl,
            videoPresentacionUrl = negocio.videoPresentacionUrl,
            landingBloques = bloquesDe(negocio),
            categoria = negocio.categoria?.let { CategoriaResumenResponse(it.id!!, it.nombre, it.icono) },
            trustScore = negocio.trustScore, nivelFormalizacion = negocio.nivelFormalizacion,
            capasVerificacion = capas, insignias = insignias, catalogo = catalogo,
            totalResenas = resenaRepository.countByNegocioId(negocio.id!!),
            promedioResenas = resenaRepository.promedioEstrellas(negocio.id!!),
            resenas = resenas,
            creadoEn = negocio.creadoEn,
        )
    }

    // ===================================================================
    // Búsqueda pública (A4/A5)
    // ===================================================================
    @Transactional(readOnly = true)
    fun buscarPublicados(categoriaId: Int?, ciudad: String?, nivel: String?, texto: String?): List<NegocioResumenPublicoResponse> {
        val negocios = negocioRepository.buscarPublicados(categoriaId, ciudad, nivel, texto)
        val alumniPorUsuario = if (negocios.isEmpty()) emptyMap() else
            alumniVerificacionRepository.findVerificadasByUsuarioIds(negocios.map { it.usuario!!.id!! }.toSet())
                .groupBy { it.usuario!!.id!! }
        return negocios.map { n ->
            NegocioResumenPublicoResponse(
                nombreComercial = n.nombreComercial, slug = n.slug,
                descripcionCorta = n.descripcionCorta, slogan = n.slogan, ciudad = n.ciudad,
                fotoPortadaUrl = n.fotoPortadaUrl, logoUrl = n.logoUrl,
                categoria = n.categoria?.let { CategoriaResumenResponse(it.id!!, it.nombre, it.icono) },
                trustScore = n.trustScore, nivelFormalizacion = n.nivelFormalizacion,
                capasVerificacion = listOf(
                    CapaVerificacionResponse("Cédula", n.usuario!!.kycLayer >= 1),
                    CapaVerificacionResponse("Correo", n.usuario!!.kycLayer >= 2),
                    CapaVerificacionResponse("Cédula revisada", n.usuario!!.fotoVerificacionEstado == "aprobada"),
                    CapaVerificacionResponse("SENESCYT/SRI", n.usuario!!.senescytSriEstado == "verificado"),
                ),
                creadoEn = n.creadoEn,
                universidadesAlumni = alumniPorUsuario[n.usuario!!.id!!].orEmpty()
                    .map { UniversidadResponse(it.universidad!!.id!!, it.universidad!!.nombre) },
            )
        }

    }

    fun ciudadesDisponibles(): List<String> = negocioRepository.ciudadesDisponibles()

    // F2 — sitemap.xml
    fun slugsPublicados(): List<String> = negocioRepository.slugsPublicados("publicado")

    // ===================================================================
    // Bandeja de solicitudes (B5)
    // ===================================================================
    @Transactional(readOnly = true)
    fun misSolicitudesRecibidas(usuarioId: UUID): List<SolicitudRecibidaResponse> {
        val negocio = miNegocioOrThrow(usuarioId)
        return solicitudRepository.findByNegocioIdOrderByCreadoEnDesc(negocio.id!!).filterNot { it.negocioOculto }.map { s ->
            val cliente = s.cliente!!
            SolicitudRecibidaResponse(
                id = s.id!!,
                cliente = ClienteResumenResponse(cliente.id!!, cliente.nombreCompleto, cliente.nombreUsuario, cliente.fotoPerfilUrl != null, cliente.telefono, cliente.kycLayer.toInt()),
                negocioLogoUrl = negocio.logoUrl,
                negocioFotoPortadaUrl = negocio.fotoPortadaUrl,
                descripcion = s.descripcion, fechaEstimada = s.fechaEstimada, estado = s.estado,
                archivada = s.negocioArchivada,
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
        solicitud.negocioArchivada = false
        solicitud.actualizadoEn = OffsetDateTime.now()
        solicitudRepository.save(solicitud)

        val cliente = solicitud.cliente!!
        val tituloNotif = when (nuevoEstado) {
            "en_conversacion" -> "${negocio.nombreComercial} respondió tu solicitud"
            "cancelada" -> "${negocio.nombreComercial} canceló tu solicitud"
            else -> "${negocio.nombreComercial} actualizó tu solicitud"
        }
        notificacionRepository.save(
            Notificacion(usuario = cliente, tipo = "solicitud", titulo = tituloNotif, mensaje = solicitud.descripcion)
        )

        return SolicitudRecibidaResponse(
            id = solicitud.id!!,
            cliente = ClienteResumenResponse(cliente.id!!, cliente.nombreCompleto, cliente.nombreUsuario, cliente.fotoPerfilUrl != null, cliente.telefono, cliente.kycLayer.toInt()),
            negocioLogoUrl = negocio.logoUrl,
            negocioFotoPortadaUrl = negocio.fotoPortadaUrl,
            descripcion = solicitud.descripcion, fechaEstimada = solicitud.fechaEstimada, estado = solicitud.estado,
            archivada = solicitud.negocioArchivada,
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
     *  - Progreso de formalización (hasta 10 pts) — pendiente=0,
     *    verificado=5, formalizado=10.
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
            "verificado" -> 5.0
            else -> 0.0
        }

        val total = (puntosIdentidad + puntosResenas + puntosCumplimiento + puntosNivel)
            .roundToInt().coerceIn(0, 100).toShort()

        negocio.trustScore = total
        negocioRepository.save(negocio)
        actualizarInsignias(negocioId)
        return total
    }

    // ===================================================================
    // Insignias y Certificaciones (B10) — se otorgan solas, con datos
    // reales; nunca se revocan una vez ganadas, ni el dueño puede pedirlas
    // a mano. Este método es idempotente: llamarlo de nuevo no duplica.
    // ===================================================================
    @Transactional
    fun actualizarInsignias(negocioId: UUID) {
        val negocio = negocioRepository.findById(negocioId).orElse(null) ?: return
        val propietario = negocio.usuario ?: return

        val totalSolicitudes = solicitudRepository.countByNegocioId(negocioId)
        val confirmadas = solicitudRepository.countByNegocioIdAndEstado(negocioId, "confirmada")
        val vendedorConfiable = totalSolicitudes >= 5 && confirmadas.toDouble() / totalSolicitudes >= 0.8

        val alumniVerificado = alumniVerificacionRepository.existsByUsuarioIdAndEstado(propietario.id!!, "verificado")

        val tieneTraduccion = planDe(negocio).incluyeTraduccion &&
            catalogoRepository.findByNegocioIdOrderByOrdenAsc(negocioId).any { it.activo && !it.nombreEn.isNullOrBlank() }

        otorgarInsignia(negocio, "Emprendedor Verificado", negocio.nivelFormalizacion == "verificado" || negocio.nivelFormalizacion == "formalizado")
        otorgarInsignia(negocio, "Vendedor Confiable", vendedorConfiable)
        otorgarInsignia(negocio, "Alumni Verificado", alumniVerificado)
        otorgarInsignia(negocio, "Potencial Exportable", tieneTraduccion)
    }

    private fun otorgarInsignia(negocio: Negocio, nombreInsignia: String, cumple: Boolean) {
        val insignia = insigniaRepository.findByNombre(nombreInsignia) ?: return
        val id = NegocioInsigniaId(negocioId = negocio.id, insigniaId = insignia.id)
        if (!cumple) {
            // Una aprobación documental revocada no puede seguir mostrando
            // la insignia de identidad completa.
            if (nombreInsignia == "Emprendedor Verificado" && negocioInsigniaRepository.existsById(id)) {
                negocioInsigniaRepository.deleteById(id)
            }
            return
        }
        if (negocioInsigniaRepository.existsById(id)) return
        negocioInsigniaRepository.save(NegocioInsignia(id = id, negocio = negocio, insignia = insignia))
    }

    @Transactional
    fun obtenerReputacion(usuarioId: UUID): ReputacionResponse {
        val negocio = miNegocioOrThrow(usuarioId)
        recalcularTrustScore(negocio.id!!)
        obtenerRutaFormalizacion(usuarioId)
        recalcularTrustScore(negocio.id!!)
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
            insignias = negocioInsigniaRepository.findByNegocioIdOrderByObtenidaEnDesc(negocio.id!!).map { it.aInsigniaResponse() },
            puntosIdentidad = listOf(
                negocio.usuario!!.kycLayer >= 1,
                negocio.usuario!!.kycLayer >= 2,
                negocio.usuario!!.fotoVerificacionEstado == "aprobada",
                negocio.usuario!!.senescytSriEstado == "verificado",
            ).count { it } * 10,
            puntosResenas = if (totalResenas > 0) (30.0 * resenaRepository.promedioEstrellas(negocio.id!!) / 5.0).roundToInt() else 0,
            puntosCumplimiento = if (totalSolicitudes > 0) (20.0 * confirmadas / totalSolicitudes).roundToInt() else 0,
            puntosFormalizacion = when (negocio.nivelFormalizacion) { "formalizado" -> 10; "verificado" -> 5; else -> 0 },
        )
    }

    @Transactional(readOnly = true)
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

    // ===================================================================
    // QR de verificación física (B11)
    // ===================================================================

    /**
     * Devuelve el código del QR del negocio, creándolo la primera vez que
     * se pide. El código es estable — una vez generado, el dueño puede
     * imprimirlo y no vuelve a cambiar, así que no tiene sentido regenerarlo
     * en cada visita a esta pantalla.
     */
    @Transactional
    fun obtenerOCrearQr(usuarioId: UUID): QrResponse {
        val negocio = miNegocioOrThrow(usuarioId)
        val existente = qrRepository.findByNegocioId(negocio.id!!)
        if (existente != null) {
            return existente.aResponse()
        }

        val qr = qrRepository.save(QrVerificacion(negocio = negocio, codigo = generarCodigoQr()))
        return qr.aResponse()
    }

    /**
     * Registra un escaneo real del QR físico: sube el contador y deja un
     * evento en analitica_eventos (misma fuente que alimentará el panel B7),
     * en la misma transacción para que ambos números no se desincronicen.
     * Es pública — cualquiera que escanee el QR la dispara sin sesión.
     */
    @Transactional
    fun registrarEscaneoQr(codigo: String): EscaneoQrResponse {
        val qr = qrRepository.findByCodigo(codigo)
            ?: throw AppException(HttpStatus.NOT_FOUND, "QR_NO_ENCONTRADO", "Este código QR no es válido")

        qr.escaneosTotal += 1
        qrRepository.save(qr)

        val negocio = qr.negocio!!
        analiticaRepository.save(AnaliticaEvento(negocio = negocio, tipoEvento = "escaneo_qr"))

        if (negocio.estadoPublicacion != "publicado") {
            throw AppException(
                HttpStatus.NOT_FOUND, "NEGOCIO_NO_PUBLICADO",
                "Este negocio ya no está disponible públicamente"
            )
        }

        return EscaneoQrResponse(slug = negocio.slug, nombreComercial = negocio.nombreComercial)
    }

    private fun generarCodigoQr(): String {
        val alfabeto = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // sin 0/O/1/I para evitar confusión al transcribir
        val random = SecureRandom()
        var codigo: String
        do {
            codigo = (1..10).map { alfabeto[random.nextInt(alfabeto.length)] }.joinToString("")
        } while (qrRepository.existsByCodigo(codigo))
        return codigo
    }

    private fun QrVerificacion.aResponse() = QrResponse(codigo = codigo, escaneosTotal = escaneosTotal, creadoEn = creadoEn)

    // ===================================================================
    // Panel de Analítica (B7)
    // ===================================================================

    /** Registra el primer contacto al abrir un chat desde la Mini Landing Page. */
    @Transactional
    fun registrarContactoChat(negocio: Negocio) {
        analiticaRepository.save(AnaliticaEvento(negocio = negocio, tipoEvento = "contacto_chat"))
    }

    @Transactional(readOnly = true)
    fun obtenerAnalitica(usuarioId: UUID): AnaliticaNegocioResponse {
        val negocio = miNegocioOrThrow(usuarioId)
        val negocioId = negocio.id!!
        val ahora = OffsetDateTime.now()

        val totalVisitas = analiticaRepository.countByNegocioIdAndTipoEvento(negocioId, "visita_validada")
        val totalClicsWhatsapp = analiticaRepository.countByNegocioIdAndTipoEvento(negocioId, "contacto_chat")
        val tasaConversion = if (totalVisitas > 0) totalClicsWhatsapp.toDouble() / totalVisitas else 0.0
        val inicioMes = ahora.withDayOfMonth(1).toLocalDate().atStartOfDay().atOffset(ahora.offset)
        val visitasMes = analiticaRepository.countByNegocioIdAndTipoEventoAndCreadoEnBetween(negocioId, "visita_validada", inicioMes, ahora)
        val solicitudesMes = solicitudRepository.countByNegocioIdAndCreadoEnBetween(negocioId, inicioMes, ahora)
        val resenasMes = resenaRepository.countByNegocioIdAndCreadoEnBetween(negocioId, inicioMes, ahora)

        // Básico ve solo los totales de arriba (siempre reales). El detalle
        // diario y las comparativas son la parte "avanzada" de B9.1.
        if (!planDe(negocio).incluyeAnaliticaAvanzada) {
            return AnaliticaNegocioResponse(
                totalVisitas = totalVisitas, totalClicsWhatsapp = totalClicsWhatsapp, tasaConversion = tasaConversion,
                avanzadaDisponible = false, serieDiaria = emptyList(),
                comparativaSemanal = ComparativaResponse(0, 0, null),
                comparativaMensual = ComparativaResponse(0, 0, null),
                visitasMes = visitasMes, solicitudesMes = solicitudesMes, resenasMes = resenasMes,
            )
        }

        val hace7 = ahora.minusDays(7)
        val hace14 = ahora.minusDays(14)
        val hace30 = ahora.minusDays(30)
        val hace60 = ahora.minusDays(60)

        val comparativaSemanal = comparar(
            analiticaRepository.countByNegocioIdAndTipoEventoAndCreadoEnBetween(negocioId, "visita_validada", hace7, ahora),
            analiticaRepository.countByNegocioIdAndTipoEventoAndCreadoEnBetween(negocioId, "visita_validada", hace14, hace7),
        )
        val comparativaMensual = comparar(
            analiticaRepository.countByNegocioIdAndTipoEventoAndCreadoEnBetween(negocioId, "visita_validada", hace30, ahora),
            analiticaRepository.countByNegocioIdAndTipoEventoAndCreadoEnBetween(negocioId, "visita_validada", hace60, hace30),
        )

        val eventosRecientes = analiticaRepository.findByNegocioIdAndTipoEventoInAndCreadoEnAfter(
            negocioId, listOf("visita_validada", "contacto_chat"), hace30
        )
        val porDia = eventosRecientes.groupBy { it.creadoEn.toLocalDate() }
        val hoy = ahora.toLocalDate()
        val serie = (29 downTo 0).map { hace ->
            val dia = hoy.minusDays(hace.toLong())
            val delDia = porDia[dia] ?: emptyList()
            PuntoSerieResponse(
                fecha = dia.toString(),
                visitas = delDia.count { it.tipoEvento == "visita_validada" }.toLong(),
                clicsWhatsapp = delDia.count { it.tipoEvento == "contacto_chat" }.toLong(),
            )
        }

        return AnaliticaNegocioResponse(
            totalVisitas = totalVisitas,
            totalClicsWhatsapp = totalClicsWhatsapp,
            tasaConversion = tasaConversion,
            avanzadaDisponible = true,
            serieDiaria = serie,
            comparativaSemanal = comparativaSemanal,
            comparativaMensual = comparativaMensual,
            visitasMes = visitasMes, solicitudesMes = solicitudesMes, resenasMes = resenasMes,
        )
    }

    private fun comparar(actual: Long, anterior: Long): ComparativaResponse {
        val variacion = if (anterior == 0L) null else ((actual - anterior).toDouble() / anterior) * 100
        return ComparativaResponse(periodoActual = actual, periodoAnterior = anterior, variacionPorcentual = variacion)
    }

    // ===================================================================
    // Suscripción / Planes (B9)
    // ===================================================================

    @Transactional(readOnly = true)
    fun listarPlanes(): List<PlanResponse> =
        planRepository.findAllByOrderByPrecioMensualAsc().map { it.aResponse() }

    /** Todo negocio nace con una suscripción básica (ver crear()); esto solo cubre negocios creados antes de B9. */
    private fun exigirPlanLanding(negocio: Negocio) {
        val suscripcion = suscripcionRepository.findByNegocioId(negocio.id!!)
        if (suscripcion == null || suscripcion.plan?.nombre !in listOf("pro", "elite") || suscripcion.estado != "activa" || suscripcion.venceEn?.isAfter(OffsetDateTime.now()) != true) {
            throw AppException(HttpStatus.PAYMENT_REQUIRED, "PLAN_REQUERIDO", "Necesitas un plan Básico o Plus activo para crear o editar tu Mini Landing Page")
        }
    }
    private fun planDe(negocio: Negocio): Plan {
        val suscripcion = suscripcionRepository.findByNegocioId(negocio.id!!)
        val vigente = suscripcion?.estado == "activa" &&
            (suscripcion.plan?.nombre == PLAN_GRATUITO || suscripcion.venceEn?.isAfter(OffsetDateTime.now()) == true)
        return if (vigente) suscripcion!!.plan!! else planRepository.findByNombre(PLAN_GRATUITO)!!
    }

    @Transactional
    fun obtenerMiSuscripcion(usuarioId: UUID): SuscripcionResponse {
        if (negocioRepository.findByUsuarioId(usuarioId) == null) crear(usuarioId, CrearNegocioRequest(nombreComercial = "Mi negocio"), prepararSuscripcion = true)
        val negocio = miNegocioOrThrow(usuarioId)
        val suscripcion = suscripcionRepository.findByNegocioId(negocio.id!!) ?: suscripcionRepository.save(
            Suscripcion(
                negocio = negocio,
                plan = planRepository.findByNombre(PLAN_GRATUITO)!!,
                ciclo = "mensual",
                estado = "activa",
            )
        )
        val totalCatalogo = catalogoRepository.countByNegocioIdAndActivoTrue(negocio.id!!)
        return suscripcion.aResponse(totalCatalogo)
    }

    /**
     * "Checkout" de plan. CheckBiz no procesa dinero (núcleo intocable) —
     * el cobro real de la suscripción se coordina fuera de la app, igual que
     * cualquier pago entre las partes. Este endpoint activa el plan elegido
     * de inmediato, como cualquier cambio de plan self-service.
     */
    @Transactional
    fun cambiarPlan(usuarioId: UUID, req: CambiarPlanRequest): SuscripcionResponse {
        val negocio = miNegocioOrThrow(usuarioId)
        exigirDueno(negocio, usuarioId)
        val plan = planRepository.findByNombre(req.planNombre)
            ?: throw AppException(HttpStatus.BAD_REQUEST, "PLAN_INVALIDO", "El plan seleccionado no existe")
        if (plan.nombre !in listOf("pro", "elite")) throw AppException(HttpStatus.BAD_REQUEST, "PLAN_INVALIDO", "Elige un plan Básico o Plus")

        if (plan.nombre == "pro") {
            val uso = mediosDe(negocio)
            if (uso.fotosUsadas > 30 || uso.videosUsados > 5) throw AppException(HttpStatus.CONFLICT, "CUPOS_PLAN", "Reduce tus medios a 30 fotos y 5 videos antes de cambiar a Básico")
        }
        val usados = catalogoRepository.countByNegocioIdAndActivoTrue(negocio.id!!)
        if (usados > plan.limiteCatalogo) {
            throw AppException(HttpStatus.CONFLICT, "CUPOS_CATALOGO", "Marca como agotados o elimina ítems hasta tener ${plan.limiteCatalogo} disponibles antes de cambiar de plan")
        }

        val suscripcion = suscripcionRepository.findByNegocioId(negocio.id!!)
            ?: Suscripcion(negocio = negocio)

        val meses = if (req.ciclo == "semestral") 6L else 1L
        suscripcion.plan = plan
        suscripcion.ciclo = req.ciclo
        suscripcion.estado = "activa"
        suscripcion.iniciaEn = OffsetDateTime.now()
        suscripcion.venceEn = if (plan.nombre == PLAN_GRATUITO) null else OffsetDateTime.now().plusMonths(meses)
        suscripcionRepository.save(suscripcion)

        val totalCatalogo = catalogoRepository.countByNegocioIdAndActivoTrue(negocio.id!!)
        return suscripcion.aResponse(totalCatalogo)
    }

    // ===================================================================
    // Multiusuario (B9.1 — Elite)
    // ===================================================================
    @Transactional(readOnly = true)
    fun listarColaboradores(usuarioId: UUID): List<ColaboradorResponse> {
        val negocio = miNegocioOrThrow(usuarioId)
        exigirDueno(negocio, usuarioId)
        return negocioColaboradorRepository.findByNegocioIdOrderByAgregadoEnAsc(negocio.id!!).map { it.aResponse() }
    }

    @Transactional
    fun invitarColaborador(usuarioId: UUID, req: InvitarColaboradorRequest): List<ColaboradorResponse> {
        val negocio = miNegocioOrThrow(usuarioId)
        exigirDueno(negocio, usuarioId)

        if (!planDe(negocio).incluyeMultiusuario) {
            throw AppException(
                HttpStatus.FORBIDDEN, "PLAN_NO_INCLUYE_MULTIUSUARIO",
                "Tu plan actual no incluye multiusuario. Mejora tu plan para invitar colaboradores."
            )
        }

        if (negocioColaboradorRepository.findByNegocioIdOrderByAgregadoEnAsc(negocio.id!!).size >= 5) {
            throw AppException(HttpStatus.CONFLICT, "LIMITE_COLABORADORES", "El plan Escala admite hasta 5 colaboradores")
        }

        val invitado = usuarioRepository.findByCorreo(req.correo.trim().lowercase()).orElseThrow {
            AppException(
                HttpStatus.NOT_FOUND, "USUARIO_NO_ENCONTRADO",
                "No hay ninguna cuenta CheckBiz con ese correo. La persona debe registrarse primero."
            )
        }

        if (invitado.id == usuarioId) {
            throw AppException(HttpStatus.BAD_REQUEST, "NO_PUEDES_INVITARTE", "Ya eres el dueño de este negocio")
        }
        if (negocioRepository.findByUsuarioId(invitado.id!!) != null) {
            throw AppException(
                HttpStatus.CONFLICT, "YA_TIENE_NEGOCIO",
                "Esa persona ya es dueña de su propio negocio en CheckBiz"
            )
        }
        val yaColabora = negocioColaboradorRepository.findByUsuarioId(invitado.id!!)
        if (yaColabora != null) {
            throw AppException(
                HttpStatus.CONFLICT, "YA_ES_COLABORADOR",
                if (yaColabora.negocio?.id == negocio.id) "Esa persona ya colabora en este negocio"
                else "Esa persona ya colabora en otro negocio"
            )
        }

        negocioColaboradorRepository.save(
            NegocioColaborador(
                id = NegocioColaboradorId(negocioId = negocio.id, usuarioId = invitado.id),
                negocio = negocio, usuario = invitado,
            )
        )
        return negocioColaboradorRepository.findByNegocioIdOrderByAgregadoEnAsc(negocio.id!!).map { it.aResponse() }
    }

    @Transactional
    fun eliminarColaborador(usuarioId: UUID, colaboradorUsuarioId: UUID): List<ColaboradorResponse> {
        val negocio = miNegocioOrThrow(usuarioId)
        exigirDueno(negocio, usuarioId)

        val colaborador = negocioColaboradorRepository.findByUsuarioId(colaboradorUsuarioId)
        if (colaborador == null || colaborador.negocio?.id != negocio.id) {
            throw AppException(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "Ese colaborador no pertenece a tu negocio")
        }
        negocioColaboradorRepository.delete(colaborador)
        return negocioColaboradorRepository.findByNegocioIdOrderByAgregadoEnAsc(negocio.id!!).map { it.aResponse() }
    }

    private fun NegocioColaborador.aResponse() = ColaboradorResponse(
        usuarioId = usuario!!.id!!,
        nombreCompleto = usuario!!.nombreCompleto,
        correo = usuario!!.correo,
        agregadoEn = agregadoEn,
    )

    // ===================================================================
    // Ruta de Formalización (B8)
    // ===================================================================

    /**
     * El único requisito que el sistema no puede verificar por sí mismo:
     * registrarse en el RIMPE ante el SRI ocurre fuera de la plataforma, sin
     * integración real con el SRI. El dueño lo marca él mismo con
     * marcarRimpeRegistrado — todos los demás requisitos se recalculan
     * siempre desde datos reales, nunca se autoreportan.
     */
    private val REQUISITO_RIMPE_REGISTRADO =
        "Registrarte en el RIMPE ante el SRI y habilitar tu facturación electrónica"

    /**
     * Evalúa los requisitos de los dos niveles contra el estado real del
     * negocio (nunca contra un checkbox que el dueño marque a mano, salvo
     * el registro RIMPE) y los deja persistidos en ruta_formalizacion.
     * El nivel refleja los requisitos actuales y puede bajar si dejan de
     * cumplirse. Los registros antiguos de semilla/asesoria quedan ignorados.
     */
    @Transactional
    fun obtenerRutaFormalizacion(usuarioId: UUID): RutaFormalizacionResponse {
        val negocio = miNegocioOrThrow(usuarioId)
        recalcularTrustScore(negocio.id!!)
        val propietario = negocio.usuario!!
        val existentes = rutaRepository.findByNegocioId(negocio.id!!)
            .associateBy { it.nivel to it.requisito }

        fun marcar(nivel: String, texto: String, cumplido: Boolean, manual: Boolean = false): RequisitoResponse {
            val fila = existentes[nivel to texto]
            val completadoFinal = if (manual) (fila?.completado ?: false) else cumplido
            val completadoEnFinal = when {
                fila != null && fila.completado == completadoFinal -> fila.completadoEn
                completadoFinal -> OffsetDateTime.now()
                else -> null
            }
            when {
                fila == null -> rutaRepository.save(
                    RutaFormalizacion(
                        negocio = negocio, nivel = nivel, requisito = texto,
                        completado = completadoFinal, completadoEn = completadoEnFinal,
                    )
                )
                fila.completado != completadoFinal -> {
                    fila.completado = completadoFinal
                    fila.completadoEn = completadoEnFinal
                    rutaRepository.save(fila)
                }
            }
            return RequisitoResponse(texto, completadoFinal, completadoEnFinal, manual)
        }

        val totalCatalogo = catalogoRepository.countByNegocioIdAndActivoTrue(negocio.id!!)
        val totalSolicitudes = solicitudRepository.countByNegocioId(negocio.id!!)
        val totalResenas = resenaRepository.countByNegocioId(negocio.id!!)

        val verificado = listOf(
            marcar("verificado", "Verificar tu identidad completa (Capas 1 a 4)", propietario.kycLayer >= 4),
            marcar("verificado", "Publicar tu Mini Landing Page", negocio.estadoPublicacion == "publicado"),
            marcar("verificado", "Agregar al menos un producto o servicio a tu catálogo", totalCatalogo > 0),
            marcar("verificado", "Recibir tu primera solicitud de un cliente", totalSolicitudes > 0),
            marcar("verificado", "Obtener tu primera reseña confirmada", totalResenas > 0),
            marcar("verificado", "Alcanzar un Trust Score de al menos 50 puntos", negocio.trustScore >= 50),
        )
        val formalizado = listOf(
            marcar("formalizado", REQUISITO_RIMPE_REGISTRADO, cumplido = false, manual = true),
            marcar("formalizado", "Mantener un Trust Score de al menos 80 puntos", negocio.trustScore >= 80),
        )

        // El nivel refleja los requisitos actuales, incluyendo la declaración SRI.
        // Desmarcarla o perder un requisito también actualiza el nivel.
        negocio.nivelFormalizacion = when {
            verificado.all { it.completado } && formalizado.all { it.completado } -> "formalizado"
            verificado.all { it.completado } -> "verificado"
            else -> "pendiente"
        }
        negocioRepository.save(negocio)
        recalcularTrustScore(negocio.id!!)

        return RutaFormalizacionResponse(
            nivelActual = negocio.nivelFormalizacion,
            niveles = listOf(
                NivelProgresoResponse("verificado", verificado, verificado.all { it.completado }),
                NivelProgresoResponse("formalizado", formalizado, formalizado.all { it.completado }),
            ),
        )
    }

    @Transactional
    fun marcarRimpeRegistrado(usuarioId: UUID, completado: Boolean): RutaFormalizacionResponse {
        val negocio = miNegocioOrThrow(usuarioId)
        val fila = rutaRepository.findByNegocioIdAndNivelAndRequisito(negocio.id!!, "formalizado", REQUISITO_RIMPE_REGISTRADO)
            ?: RutaFormalizacion(negocio = negocio, nivel = "formalizado", requisito = REQUISITO_RIMPE_REGISTRADO)
        fila.completado = completado
        fila.completadoEn = if (completado) OffsetDateTime.now() else null
        rutaRepository.save(fila)
        return obtenerRutaFormalizacion(usuarioId)
    }

    private fun Resena.aDetalleResponse() = ResenaDetalleResponse(
        id = id!!, clienteId = cliente?.takeIf { it.estadoCedula == "activa" }?.id,
        clienteNombre = cliente?.takeIf { it.estadoCedula == "activa" }?.let { it.nombreUsuario?.let { nombre -> "@$nombre" } ?: it.nombreCompleto } ?: "Usuario eliminado",
        clienteTieneFoto = cliente?.estadoCedula == "activa" && cliente?.fotoPerfilUrl != null,
        estrellas = estrellas, comentario = comentario,
        respuestaNegocio = respuestaNegocio, respondidaEn = respondidaEn, creadoEn = creadoEn,
    )

    // ===================================================================
    // B9.1 — un colaborador opera el mismo negocio que su dueño en cualquier
    // endpoint que pase por aquí (editor, catálogo, solicitudes, reputación,
    // analítica, formalización, QR, imágenes). Lo que un colaborador NUNCA
    // puede hacer (cambiar de plan, invitar/quitar colaboradores, eliminar
    // la cuenta) se valida aparte, comparando negocio.usuario?.id.
    private fun miNegocioOrThrow(usuarioId: UUID): Negocio {
        negocioRepository.findByUsuarioId(usuarioId)?.let { return it }
        negocioColaboradorRepository.findByUsuarioId(usuarioId)?.negocio?.let { return it }
        throw AppException(HttpStatus.NOT_FOUND, "SIN_NEGOCIO", "Todavía no has creado tu negocio")
    }

    private fun esDueno(negocio: Negocio, usuarioId: UUID) = negocio.usuario?.id == usuarioId

    private fun exigirDueno(negocio: Negocio, usuarioId: UUID) {
        if (!esDueno(negocio, usuarioId)) {
            throw AppException(
                HttpStatus.FORBIDDEN, "SOLO_DUENO",
                "Solo el dueño del negocio puede hacer esto"
            )
        }
    }

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

    private fun Negocio.aResponse(totalCatalogo: Long, usuarioId: UUID) = NegocioResponse(
        id = id!!, nombreComercial = nombreComercial, slug = slug,
        descripcionCorta = descripcionCorta, slogan = slogan, ciudad = ciudad, whatsapp = whatsapp,
        fotoPortadaUrl = fotoPortadaUrl, logoUrl = logoUrl, videoPresentacionUrl = videoPresentacionUrl,
        landingBloques = bloquesDe(this),
        categoria = categoria?.let { CategoriaResumenResponse(it.id!!, it.nombre, it.icono) },
        trustScore = trustScore, nivelFormalizacion = nivelFormalizacion,
        estadoPublicacion = estadoPublicacion, totalCatalogo = totalCatalogo,
        insignias = negocioInsigniaRepository.findByNegocioIdOrderByObtenidaEnDesc(id!!).map { it.aInsigniaResponse() },
        esDueno = esDueno(this, usuarioId),
        creadoEn = creadoEn, actualizadoEn = actualizadoEn,
    )

    private fun NegocioInsignia.aInsigniaResponse() = InsigniaResponse(
        nombre = insignia!!.nombre, descripcion = insignia!!.descripcion,
        icono = insignia!!.icono, obtenidaEn = obtenidaEn,
    )

    private fun CatalogoItem.aResponse() = ItemCatalogoResponse(
        id = id!!, nombre = nombre, precioReferencial = precioReferencial,
        fotoUrl = fotoUrl, orden = orden, activo = activo, nombreEn = nombreEn, creadoEn = creadoEn,
    )

    private fun Plan.aResponse() = PlanResponse(
        nombre = nombre, precioMensual = precioMensual, precioSemestral = precioSemestral,
        limiteCatalogo = limiteCatalogo, incluyeVideo = incluyeVideo,
        incluyeAnaliticaAvanzada = incluyeAnaliticaAvanzada, incluyeMultiusuario = incluyeMultiusuario,
        incluyeTraduccion = incluyeTraduccion, incluyeCertificadoPdf = incluyeCertificadoPdf,
        incluyeWhatsappBusinessApi = incluyeWhatsappBusinessApi,
    )

    private fun Suscripcion.aResponse(totalCatalogoUsado: Long) = SuscripcionResponse(
        plan = plan!!.aResponse(), ciclo = ciclo, estado = estado,
        iniciaEn = iniciaEn, venceEn = venceEn, totalCatalogoUsado = totalCatalogoUsado,
    )
}

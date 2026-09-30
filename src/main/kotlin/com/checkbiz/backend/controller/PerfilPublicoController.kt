package com.checkbiz.backend.controller

import com.checkbiz.backend.exception.AppException
import com.checkbiz.backend.repository.NegocioRepository
import com.checkbiz.backend.repository.UsuarioRepository
import com.checkbiz.backend.service.ArchivoService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.util.UUID

data class NegocioPerfilPublico(val slug: String, val nombre: String, val logoUrl: String?)
data class PerfilClientePublico(
    val id: UUID,
    val nombre: String,
    val nombreUsuario: String?,
    val estado: String?,
    val descripcion: String?,
    val tieneFoto: Boolean,
    val tieneBanner: Boolean,
    val esEmprendedor: Boolean,
    val favorito: NegocioPerfilPublico?,
    val guardados: List<NegocioPerfilPublico>,
    val miembroDesde: OffsetDateTime,
)

@RestController
@RequestMapping("/api/perfiles")
class PerfilPublicoController(
    private val usuarios: UsuarioRepository,
    private val negocios: NegocioRepository,
    private val archivos: ArchivoService,
) {
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    fun perfil(@PathVariable id: UUID): PerfilClientePublico {
        val usuario = usuarios.findById(id).orElseThrow {
            AppException(HttpStatus.NOT_FOUND, "PERFIL_NO_ENCONTRADO", "No encontramos este perfil")
        }
        if (usuario.estadoCedula == "eliminada") throw AppException(HttpStatus.NOT_FOUND, "PERFIL_NO_ENCONTRADO", "No encontramos este perfil")
        fun negocio(slug: String) = negocios.findBySlugAndEstadoPublicacion(slug, "publicado")
            ?.let { NegocioPerfilPublico(it.slug, it.nombreComercial, it.logoUrl) }
        return PerfilClientePublico(
            id = id,
            nombre = usuario.nombreUsuario ?: usuario.nombreCompleto,
            nombreUsuario = usuario.nombreUsuario,
            estado = usuario.estadoPerfil,
            descripcion = usuario.descripcionPerfil,
            tieneFoto = usuario.fotoPerfilUrl != null,
            tieneBanner = usuario.bannerPerfilUrl != null,
            esEmprendedor = usuario.rolEmprendedor,
            favorito = usuario.negocioFavoritoSlug?.let(::negocio),
            guardados = usuario.negociosGuardadosSlugs?.split(",")?.filter { it.isNotBlank() }
                ?.take(5)?.mapNotNull(::negocio) ?: emptyList(),
            miembroDesde = usuario.creadoEn,
        )
    }

    @GetMapping("/{id}/foto")
    fun foto(@PathVariable id: UUID): ResponseEntity<ByteArray> = imagen(id, false)

    @GetMapping("/{id}/banner")
    fun banner(@PathVariable id: UUID): ResponseEntity<ByteArray> = imagen(id, true)

    private fun imagen(id: UUID, banner: Boolean): ResponseEntity<ByteArray> {
        val usuario = usuarios.findById(id).orElseThrow {
            AppException(HttpStatus.NOT_FOUND, "PERFIL_NO_ENCONTRADO", "No encontramos este perfil")
        }
        if (usuario.estadoCedula == "eliminada") throw AppException(HttpStatus.NOT_FOUND, "PERFIL_NO_ENCONTRADO", "No encontramos este perfil")
        val url = if (banner) usuario.bannerPerfilUrl else usuario.fotoPerfilUrl
        if (url == null) throw AppException(HttpStatus.NOT_FOUND, "SIN_IMAGEN", "Este perfil no tiene esa imagen")
        val (bytes, tipo) = archivos.leerImagen(url)
        return ResponseEntity.ok().contentType(tipo).body(bytes)
    }
}

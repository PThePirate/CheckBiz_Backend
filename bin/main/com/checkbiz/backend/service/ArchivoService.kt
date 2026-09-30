package com.checkbiz.backend.service

import com.checkbiz.backend.exception.AppException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.ByteBuffer
import java.util.UUID

@Service
class ArchivoService(
    @Value("\${checkbiz.uploads.dir}") private val uploadsDir: String,
) {
    private val tiposPermitidos = setOf("image/jpeg", "image/png", "image/webp")
    private val tiposPortadaLogo = setOf("image/jpeg", "image/png")

    fun guardarPortadaOLogo(file: MultipartFile): String {
        val extension = file.originalFilename?.substringAfterLast('.', "")?.lowercase()
        val extensionValida = when (file.contentType) {
            "image/jpeg" -> extension == "jpg"
            "image/png" -> extension == "png"
            else -> false
        }
        if (file.contentType !in tiposPortadaLogo || !extensionValida) {
            throw AppException(HttpStatus.BAD_REQUEST, "FORMATO_INVALIDO", "Solo se permiten imágenes en .jpg y .png")
        }
        return guardarImagenNegocio(file)
    }

    fun validarImagen(file: MultipartFile) {
        if (file.isEmpty) throw AppException(HttpStatus.BAD_REQUEST, "FOTO_REQUERIDA", "Debes adjuntar ambas caras de la cédula")
        if (file.contentType !in tiposPermitidos) throw AppException(HttpStatus.BAD_REQUEST, "FORMATO_INVALIDO", "Formato no permitido. Usa JPG, PNG o WEBP.")
    }

    /** Solo borra archivos privados creados por este servicio, nunca rutas externas. */
    fun eliminarImagenPrivada(fotoUrl: String) {
        val nombre = Path.of(fotoUrl).fileName.toString()
        if (!Regex("[0-9a-fA-F-]{36}\\.(jpg|png|webp)").matches(nombre)) return
        Files.deleteIfExists(Path.of(uploadsDir).toAbsolutePath().normalize().resolve(nombre))
    }

    /**
     * Guarda una imagen en disco y devuelve la ruta pública relativa
     * (/uploads/...). Genérico: lo usan tanto la foto de verificación KYC
     * como la foto de perfil (A9) — la diferencia de sensibilidad entre
     * ambas la maneja quien decide cómo se sirve cada una, no este método.
     */
    fun guardarImagen(file: MultipartFile): String {
        if (file.isEmpty) {
            throw AppException(HttpStatus.BAD_REQUEST, "FOTO_REQUERIDA", "Debes adjuntar una imagen")
        }
        if (file.contentType !in tiposPermitidos) {
            throw AppException(HttpStatus.BAD_REQUEST, "FORMATO_INVALIDO", "Formato no permitido. Usa JPG, PNG o WEBP.")
        }

        val dir = Path.of(uploadsDir)
        Files.createDirectories(dir)

        val ext = when (file.contentType) {
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            else -> ".jpg"
        }
        val nombre = "${UUID.randomUUID()}$ext"
        val destino = dir.resolve(nombre)
        file.transferTo(destino)

        return "/uploads/$nombre"
    }

    /**
     * Lee del disco una imagen ya guardada (KYC o foto de perfil). Ninguna
     * se sirve como archivo estático público — siempre a través de un
     * endpoint autenticado que primero valida quién puede pedirla (el
     * dueño, un admin, o cualquiera si es una foto de perfil pública).
     *
     * Se usa fileName() sobre la URL guardada para quedarnos solo con el
     * nombre del archivo, así una "fotoUrl" con "../" no puede escapar del
     * directorio de subidas.
     */
    fun leerImagen(fotoUrl: String): Pair<ByteArray, MediaType> {
        val baseDir = Path.of(uploadsDir).toAbsolutePath().normalize()
        val nombre = Path.of(fotoUrl).fileName.toString()
        val archivo = baseDir.resolve(nombre).normalize()

        if (!archivo.startsWith(baseDir) || !Files.exists(archivo)) {
            throw AppException(HttpStatus.NOT_FOUND, "ARCHIVO_NO_ENCONTRADO", "El archivo no existe")
        }

        val tipo = when {
            nombre.endsWith(".png") -> MediaType.IMAGE_PNG
            nombre.endsWith(".webp") -> MediaType.parseMediaType("image/webp")
            else -> MediaType.IMAGE_JPEG
        }
        return Files.readAllBytes(archivo) to tipo
    }

    /**
     * Logo, portada y fotos de catálogo (A6/B3/B4) son públicas por
     * naturaleza — se muestran en la Mini Landing Page sin sesión. Se
     * guardan en un subdirectorio propio, físicamente separado del de KYC
     * y fotos de perfil, para que servirlas por un endpoint público jamás
     * pueda alcanzar un documento de identidad aunque alguien adivinara su
     * nombre de archivo.
     */
    fun guardarImagenNegocio(file: MultipartFile): String {
        if (file.isEmpty) {
            throw AppException(HttpStatus.BAD_REQUEST, "FOTO_REQUERIDA", "Debes adjuntar una imagen")
        }
        if (file.contentType !in tiposPermitidos) {
            throw AppException(HttpStatus.BAD_REQUEST, "FORMATO_INVALIDO", "Formato no permitido. Usa JPG, PNG o WEBP.")
        }

        val dir = Path.of(uploadsDir, "negocios")
        Files.createDirectories(dir)

        val ext = when (file.contentType) {
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            else -> ".jpg"
        }
        val nombre = "${UUID.randomUUID()}$ext"
        file.transferTo(dir.resolve(nombre))

        // Ruta relativa al backend, no a /uploads: la sirve NegocioPublicoController
        // (imágenes públicas), nunca un recurso estático directo.
        return "/api/negocios/imagenes/$nombre"
    }

    /** MP4 público de la landing. Lee la duración del encabezado del contenedor en el servidor. */
    fun guardarVideoNegocio(file: MultipartFile): String {
        if (file.isEmpty || file.contentType != "video/mp4" || !file.originalFilename.orEmpty().endsWith(".mp4", true)) {
            throw AppException(HttpStatus.BAD_REQUEST, "VIDEO_INVALIDO", "Sube un video MP4")
        }
        if (file.size > 25L * 1024 * 1024) throw AppException(HttpStatus.BAD_REQUEST, "VIDEO_GRANDE", "El video no puede superar 25 MB")
        val bytes = file.bytes
        val duracion = duracionMp4(bytes) ?: throw AppException(HttpStatus.BAD_REQUEST, "VIDEO_INVALIDO", "No se pudo leer la duración del video MP4")
        if (duracion <= 0 || duracion > 45) throw AppException(HttpStatus.BAD_REQUEST, "VIDEO_LARGO", "El video debe durar hasta 45 segundos")
        val dir = Path.of(uploadsDir, "negocios", "videos")
        Files.createDirectories(dir)
        val nombre = "${UUID.randomUUID()}.mp4"
        Files.write(dir.resolve(nombre), bytes)
        return "/api/negocios/videos/$nombre"
    }

    private fun duracionMp4(bytes: ByteArray): Double? {
        if (bytes.size < 16 || String(bytes, 4, 4, Charsets.US_ASCII) != "ftyp") return null
        val data = ByteBuffer.wrap(bytes)
        fun buscar(inicio: Int, fin: Int, objetivo: String): Int? {
            var pos = inicio
            while (pos + 8 <= fin) {
                val tam = data.getInt(pos).toLong() and 0xffffffffL
                val tipo = String(bytes, pos + 4, 4, Charsets.US_ASCII)
                val cabecera = if (tam == 1L) 16 else 8
                val longitud = if (tam == 1L && pos + 16 <= fin) data.getLong(pos + 8) else if (tam == 0L) (fin - pos).toLong() else tam
                if (longitud < cabecera || longitud > fin - pos) return null
                if (tipo == objetivo) return pos + cabecera
                if (tipo == "moov") return buscar(pos + cabecera, pos + longitud.toInt(), objetivo)
                pos += longitud.toInt()
            }
            return null
        }
        val mvhd = buscar(0, bytes.size, "mvhd") ?: return null
        if (mvhd >= bytes.size) return null
        val version = bytes[mvhd].toInt()
        if (version !in 0..1) return null
        val escalaOffset = mvhd + if (version == 1) 20 else 12
        val duracionOffset = escalaOffset + 4
        if (duracionOffset + (if (version == 1) 8 else 4) > bytes.size) return null
        val escala = data.getInt(escalaOffset).toLong() and 0xffffffffL
        val unidades = if (version == 1) data.getLong(duracionOffset) else data.getInt(duracionOffset).toLong() and 0xffffffffL
        return if (escala > 0 && unidades >= 0) unidades.toDouble() / escala else null
    }

    fun leerVideoNegocio(nombre: String): ByteArray {
        if (!Regex("[0-9a-fA-F-]{36}\\.mp4").matches(nombre)) throw AppException(HttpStatus.NOT_FOUND, "VIDEO_NO_ENCONTRADO", "Video no encontrado")
        val dir = Path.of(uploadsDir, "negocios", "videos").toAbsolutePath().normalize()
        val ruta = dir.resolve(nombre).normalize()
        if (!ruta.startsWith(dir) || !Files.exists(ruta)) throw AppException(HttpStatus.NOT_FOUND, "VIDEO_NO_ENCONTRADO", "Video no encontrado")
        return Files.readAllBytes(ruta)
    }

    /** Lee del subdirectorio "negocios" — nunca puede alcanzar el de KYC/perfil. */
    fun leerImagenNegocio(fotoUrl: String): Pair<ByteArray, MediaType> {
        val baseDir = Path.of(uploadsDir, "negocios").toAbsolutePath().normalize()
        val nombre = Path.of(fotoUrl).fileName.toString()
        val archivo = baseDir.resolve(nombre).normalize()

        if (!archivo.startsWith(baseDir) || !Files.exists(archivo)) {
            throw AppException(HttpStatus.NOT_FOUND, "ARCHIVO_NO_ENCONTRADO", "El archivo no existe")
        }

        val tipo = when {
            nombre.endsWith(".png") -> MediaType.IMAGE_PNG
            nombre.endsWith(".webp") -> MediaType.parseMediaType("image/webp")
            else -> MediaType.IMAGE_JPEG
        }
        return Files.readAllBytes(archivo) to tipo
    }
}

package com.checkbiz.backend.service

import com.checkbiz.backend.exception.AppException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@Service
class ArchivoService(
    @Value("\${checkbiz.uploads.dir}") private val uploadsDir: String,
) {
    private val tiposPermitidos = setOf("image/jpeg", "image/png", "image/webp")

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
}

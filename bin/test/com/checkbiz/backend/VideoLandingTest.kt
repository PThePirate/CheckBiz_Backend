package com.checkbiz.backend

import com.checkbiz.backend.exception.AppException
import com.checkbiz.backend.service.ArchivoService
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.web.MockMultipartFile
import java.nio.ByteBuffer
import java.nio.file.Path

class VideoLandingTest {
    @TempDir lateinit var uploads: Path

    private fun mp4(segundos: Int): ByteArray = ByteBuffer.allocate(52)
        .putInt(16).put("ftyp".toByteArray()).put("isom".toByteArray()).putInt(0)
        .putInt(36).put("moov".toByteArray())
        .putInt(28).put("mvhd".toByteArray())
        .putInt(0).putInt(0).putInt(0).putInt(1000).putInt(segundos * 1000)
        .array()

    @Test fun `acepta video mp4 de hasta 45 segundos`() {
        val bytes = mp4(45)
        val ruta = ArchivoService(uploads.toString()).guardarVideoNegocio(MockMultipartFile("foto", "demo.mp4", "video/mp4", bytes))
        assertArrayEquals(bytes, ArchivoService(uploads.toString()).leerVideoNegocio(ruta.substringAfterLast('/')))
    }

    @Test fun `rechaza videos de mas de 45 segundos`() {
        val archivo = MockMultipartFile("foto", "demo.mp4", "video/mp4", mp4(46))
        assertThrows(AppException::class.java) { ArchivoService(uploads.toString()).guardarVideoNegocio(archivo) }
    }
}

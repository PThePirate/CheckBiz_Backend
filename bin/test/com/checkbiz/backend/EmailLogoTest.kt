package com.checkbiz.backend

import com.checkbiz.backend.service.EmailService
import com.checkbiz.backend.service.EmailTemplates
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.mail.javamail.JavaMailSender
import java.util.Properties

class EmailLogoTest {
    @Test
    fun `logo remoto envia solo HTML sin ninguna parte de imagen`() {
        val sender = mock(JavaMailSender::class.java)
        val message = MimeMessage(Session.getInstance(Properties()))
        `when`(sender.createMimeMessage()).thenReturn(message)
        val service = EmailService(sender, "sender@example.com", true, "https://example.com/brand/logo.png")
        service.enviar("recipient@example.com", "Verificación", EmailTemplates.codigoVerificacion("Prueba", "Verificación", "Tu código", "123456"))
        message.saveChanges()
        verify(sender).send(message)
        assertTrue(message.isMimeType("text/html"))
        assertFalse(message.isMimeType("multipart/*"))
        assertTrue(message.content.toString().contains("https://example.com/brand/logo.png"))
        assertFalse(message.content.toString().contains("cid:"))
    }

    @Test
    fun `logo pertenece al HTML sin nombre de archivo ni contenedor de adjuntos`() {
        val sender = mock(JavaMailSender::class.java)
        val message = MimeMessage(Session.getInstance(Properties()))
        `when`(sender.createMimeMessage()).thenReturn(message)
        val service = EmailService(sender, "sender@example.com", true)
        service.enviar("recipient@example.com", "Verificación", EmailTemplates.codigoVerificacion("Prueba", "Verificación", "Tu código", "123456"))
        message.saveChanges()
        verify(sender).send(message)
        assertTrue(message.isMimeType("multipart/related"))
        val parts = message.content as MimeMultipart
        assertEquals(2, parts.count)
        assertTrue(parts.getBodyPart(0).content.toString().contains("cid:checkbiz-logo"))
        val logo = parts.getBodyPart(1)
        assertEquals("inline", logo.disposition)
        assertNull(logo.fileName)
        assertTrue(logo.isMimeType("image/png"))
        assertEquals("<checkbiz-logo>", logo.getHeader("Content-ID").single())
        assertFalse(logo.contentType.contains("name=", ignoreCase = true))
        assertTrue(logo.inputStream.readBytes().isNotEmpty())
    }
}

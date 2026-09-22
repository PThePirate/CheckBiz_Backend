package com.checkbiz.backend.service

import com.checkbiz.backend.exception.AppException
import jakarta.mail.internet.MimeMessage
import jakarta.mail.util.ByteArrayDataSource
import org.springframework.core.io.ClassPathResource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.springframework.web.util.HtmlUtils
import java.net.URI

/**
 * Envío real de correo vía SMTP — funciona con Gmail, Outlook/Office365, o
 * cualquier proveedor SMTP estándar. Usa JavaMailSender, que Spring Boot
 * configura solo a partir de las propiedades spring.mail.* (ver
 * application.yml / .env.example).
 *
 * GMAIL: necesitas una "Contraseña de aplicación", no la contraseña normal
 * de tu cuenta. Se genera en: Cuenta de Google > Seguridad > Verificación
 * en 2 pasos (debe estar activada) > Contraseñas de aplicaciones.
 *
 * OUTLOOK / OFFICE365: mismo caso — contraseña de aplicación si tienes MFA
 * activo. OJO: Microsoft está retirando la autenticación básica SMTP para
 * cuentas corporativas/educativas (Exchange Online) — esas necesitan
 * OAuth2, no usuario+contraseña. Esto solo funciona tal cual con cuentas
 * personales (outlook.com / hotmail.com).
 *
 * IMPORTANTE — esto no se pudo probar contra un servidor SMTP real: el
 * entorno donde se escribió este código no tiene salida de red hacia
 * smtp.gmail.com ni smtp.office365.com. La integración sigue el mecanismo
 * estándar de JavaMailSender de Spring Boot, pero la primera prueba real
 * con una cuenta de correo verdadera la tienes que hacer tú.
 */
@Service
class EmailService(
    private val mailSender: JavaMailSender,
    @Value("\${checkbiz.email.from:}") private val from: String,
    @Value("\${checkbiz.email.habilitado:false}") private val habilitado: Boolean,
    @Value("\${checkbiz.email.logo-url:}") private val logoUrl: String = "",
) {
    private val log = LoggerFactory.getLogger(EmailService::class.java)

    val configurado: Boolean
        get() = habilitado && from.isNotBlank()

    fun enviar(destinatario: String, asunto: String, cuerpoHtml: String) {
        if (!configurado) {
            throw AppException(
                HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_NO_CONFIGURADO",
                "El envío de correo aún no está disponible. Inténtalo más tarde."
            )
        }

        try {
            val url = logoUrl.trim()
            if (url.isNotEmpty()) {
                val uri = URI(url)
                require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) {
                    "EMAIL_LOGO_URL debe ser una URL HTTPS pública de la imagen"
                }
            }
            val usaLogoRemoto = url.isNotEmpty()
            val html = if (usaLogoRemoto) cuerpoHtml.replace("cid:checkbiz-logo", HtmlUtils.htmlEscape(url)) else cuerpoHtml
            val mensaje: MimeMessage = mailSender.createMimeMessage()
            val modo = if (usaLogoRemoto) MimeMessageHelper.MULTIPART_MODE_NO else MimeMessageHelper.MULTIPART_MODE_RELATED
            val helper = MimeMessageHelper(mensaje, modo, "UTF-8")
            helper.setFrom(from)
            helper.setTo(destinatario)
            helper.setSubject(asunto)
            helper.setText(html, true)
            if (!usaLogoRemoto && cuerpoHtml.contains("cid:checkbiz-logo")) {
                val logo = ClassPathResource("brand/checkbiz-mark.png").inputStream.use { ByteArrayDataSource(it, "image/png") }
                logo.name = "" // Sin nombre de archivo: es parte del HTML, no un adjunto descargable.
                helper.addInline("checkbiz-logo", logo)
            }
            mailSender.send(mensaje)
        } catch (ex: MailException) {
            log.error("Error enviando correo SMTP a $destinatario", ex)
            throw AppException(
                HttpStatus.BAD_GATEWAY, "EMAIL_ENVIO_FALLIDO",
                "No pudimos enviar el correo. Intenta de nuevo en unos minutos."
            )
        }
    }
}

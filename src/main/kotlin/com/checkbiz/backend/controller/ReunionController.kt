package com.checkbiz.backend.controller

import com.checkbiz.backend.exception.AppException
import com.checkbiz.backend.service.EmailService
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.*
import org.springframework.web.util.HtmlUtils
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

data class ReunionRequest(
    @field:NotBlank @field:Size(max=150) val nombre: String,
    @field:NotBlank @field:Size(max=150) val cargo: String,
    @field:NotBlank @field:Size(max=150) val universidad: String,
    @field:Pattern(regexp="Emprendimiento|Vinculación con la sociedad|Bienestar estudiantil|Seguimiento a graduados|Rectorado o vicerrectorado|Otra") val area: String,
    @field:NotBlank @field:Email @field:Size(max=254) val correo: String,
    @field:Size(max=150) val telefono: String = "",
    @field:Pattern(regexp="|Bronze|Silver|Gold|Todavía no sé") val nivel: String = "",
    @field:Pattern(regexp="|Menos de 100|100 a 200|Más de 200|No sé") val estudiantes: String = "",
    @field:Pattern(regexp="|Presencial|Virtual") val modalidad: String = "",
    @field:Size(max=2000) val mensaje: String = "",
    @field:AssertTrue val privacidad: Boolean,
    @field:NotBlank @field:Size(max=2048) val turnstileToken: String,
)

@RestController
class ReunionController(
    private val email: EmailService,
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    @Value("\${TURNSTILE_SECRET_KEY:}") private val secret: String,
    @Value("\${MEETING_TEAM_EMAIL:}") private val team: String,
) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()

    @PostMapping("/api/reuniones")
    @ResponseStatus(HttpStatus.CREATED)
    fun crear(@Valid @RequestBody req: ReunionRequest): Map<String,String> {
        if(secret.isBlank() || team.isBlank() || !email.configurado) throw AppException(HttpStatus.SERVICE_UNAVAILABLE,"REUNIONES_NO_CONFIGURADAS","El envío aún no está disponible. Escribe a hola@checkbiz.ec para solicitar una reunión.")
        val encoded = "secret=${URLEncoder.encode(secret, Charsets.UTF_8)}&response=${URLEncoder.encode(req.turnstileToken, Charsets.UTF_8)}"
        val result = try {
            val request = HttpRequest.newBuilder(URI("https://challenges.cloudflare.com/turnstile/v0/siteverify"))
                .timeout(Duration.ofSeconds(12)).header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encoded)).build()
            mapper.readTree(client.send(request,HttpResponse.BodyHandlers.ofString()).body())
        } catch(ex: Exception) { throw AppException(HttpStatus.BAD_GATEWAY,"PROTECCION_NO_DISPONIBLE","No se pudo comprobar la protección. Inténtalo de nuevo.") }
        if(!result.path("success").asBoolean() || result.path("action").asText() != "meeting") throw AppException(HttpStatus.BAD_REQUEST,"PROTECCION_INVALIDA","Completa nuevamente la protección antes de enviar.")
        val id = UUID.randomUUID()
        val values = linkedMapOf("Nombre" to req.nombre,"Cargo" to req.cargo,"Universidad" to req.universidad,"Área" to req.area,"Correo" to req.correo,"Teléfono" to req.telefono,"Nivel" to req.nivel,"Estudiantes" to req.estudiantes,"Modalidad" to req.modalidad,"Mensaje" to req.mensaje)
        val payload = mapper.writeValueAsString(values)
        jdbc.update("INSERT INTO solicitudes_reunion (id, correo, datos, estado) VALUES (?, ?, CAST(? AS jsonb), 'pendiente_envio')",id,req.correo.trim(),payload)
        val rows = values.entries.joinToString("") { "<p><b>${HtmlUtils.htmlEscape(it.key)}:</b> ${HtmlUtils.htmlEscape(it.value)}</p>" }
        try {
            email.enviar(team,"Solicitud de reunión universitaria · ${req.nivel}","<h2>Nueva solicitud</h2>$rows")
            email.enviar(req.correo.trim(),"Recibimos tu solicitud · CheckBiz","<h2>Gracias por contactarnos</h2><p>Te escribiremos en los próximos 2 días hábiles para coordinar la reunión.</p>$rows")
            jdbc.update("UPDATE solicitudes_reunion SET estado='enviada' WHERE id=?",id)
        } catch(ex: Exception) {
            jdbc.update("UPDATE solicitudes_reunion SET estado='error_envio' WHERE id=?",id)
            throw AppException(HttpStatus.BAD_GATEWAY,"REUNION_CORREO_FALLIDO","No pudimos completar los correos. Escribe a hola@checkbiz.ec e indica la referencia $id.")
        }
        return mapOf("id" to id.toString(),"mensaje" to "Solicitud recibida")
    }
}

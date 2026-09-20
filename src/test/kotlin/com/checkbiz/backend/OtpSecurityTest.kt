package com.checkbiz.backend

import com.checkbiz.backend.config.*
import com.checkbiz.backend.controller.AuthController
import com.checkbiz.backend.repository.UsuarioRepository
import com.checkbiz.backend.service.ArchivoService
import com.checkbiz.backend.service.AuthService
import com.checkbiz.backend.service.OtpService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.OffsetDateTime
import java.util.UUID

@WebMvcTest(AuthController::class, properties = ["checkbiz.jwt.secret=otp-test-secret-at-least-32-characters", "checkbiz.jwt.expiration-ms=60000"])
@Import(SecurityConfig::class, JwtAuthFilter::class, JwtService::class)
class OtpSecurityTest {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var jwt: JwtService
    @MockBean lateinit var authService: AuthService
    @MockBean lateinit var archivoService: ArchivoService
    @MockBean lateinit var usuarioRepository: UsuarioRepository

    @Test
    fun `sin sesion devuelve 401 con mensaje`() {
        mvc.perform(post("/api/auth/otp/enviar"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("NO_AUTENTICADO"))
    }

    @Test
    fun `token invalido devuelve 401`() {
        mvc.perform(post("/api/auth/otp/enviar").header("Authorization", "Bearer invalido"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `admin no puede reenviar otp de usuario`() {
        val token = jwt.generarTokenAdmin(AdminClaims(UUID.randomUUID(), "admin"))
        mvc.perform(post("/api/auth/otp/enviar").header("Authorization", "Bearer $token"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("ACCESO_DENEGADO"))
    }

    @Test
    fun `usuario autenticado puede reenviar codigo de prueba`() {
        val id = UUID.randomUUID()
        val token = jwt.generarTokenUsuario(UsuarioClaims(id, true, false, 1))
        `when`(usuarioRepository.estadoCedulaDe(id)).thenReturn("activa")
        `when`(authService.enviarOtp(id, "email")).thenReturn(OtpService.EnvioResultado(OffsetDateTime.now().plusMinutes(5), "123456"))
        mvc.perform(post("/api/auth/otp/enviar").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.otp.codigoDev").value("123456"))
            .andExpect(jsonPath("$.mensaje").value("Código de prueba generado. No se ha enviado un correo."))
    }

    @Test
    fun `usuario vetado con token todavia vigente es rechazado`() {
        val id = UUID.randomUUID()
        val token = jwt.generarTokenUsuario(UsuarioClaims(id, true, false, 1))
        `when`(usuarioRepository.estadoCedulaDe(id)).thenReturn("vetada")
        mvc.perform(post("/api/auth/otp/enviar").header("Authorization", "Bearer $token"))
            .andExpect(status().isUnauthorized)
    }
}
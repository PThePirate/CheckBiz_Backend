package com.checkbiz.backend

import com.checkbiz.backend.config.registroPasswordEncoder
import com.checkbiz.backend.dto.RegistroRequest
import jakarta.validation.Validation
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class RegistroValidationTest {
    private val base = RegistroRequest("Usuario de prueba", "prueba@example.com", "0991234567", "1710034065", "Prueba-segura-123", true)

    @Test
    fun `valida limites del registro y pais`() {
        Validation.buildDefaultValidatorFactory().use { factory ->
            val validator = factory.validator
            assertTrue(validator.validate(base).isEmpty())
            assertTrue(validator.validate(base.copy(nombreCompleto = "a".repeat(80), password = "ñ".repeat(80))).isEmpty())
            for (request in listOf(
                base.copy(nombreCompleto = "a".repeat(81)), base.copy(correo = "a".repeat(81) + "@example.com"),
                base.copy(correo = "sin-arroba"), base.copy(telefono = "099123456"), base.copy(telefono = "09912345678"),
                base.copy(telefono = "09912a4567"), base.copy(cedula = "17100340655"),
                base.copy(password = "a".repeat(81)), base.copy(password = "1234567"),
                base.copy(pais = "XX"), base.copy(aceptoTerminos = false),
            )) assertFalse(validator.validate(request).isEmpty())
        }
    }

    @Test
    fun `contrasena de 80 caracteres conserva todos sus bytes`() {
        val encoder = registroPasswordEncoder()
        val password = "ñ".repeat(80)
        val hash = encoder.encode(password)
        assertTrue(encoder.matches(password, hash))
        assertFalse(encoder.matches("ñ".repeat(79) + "a", hash))
        assertFalse(encoder.matches(password.take(72), hash))
    }

    @Test
    fun `cuentas bcrypt existentes mantienen acceso`() {
        val encoder = registroPasswordEncoder()
        val hash = BCryptPasswordEncoder(4).encode(base.password)
        assertTrue(encoder.matches(base.password, hash))
        assertTrue(encoder.matches(base.password, "{bcrypt}$hash"))
        assertFalse(encoder.matches("incorrecta", hash))
    }
}

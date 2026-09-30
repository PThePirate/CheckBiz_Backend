package com.checkbiz.backend

import com.checkbiz.backend.controller.ReunionRequest
import jakarta.validation.Validation
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ReunionValidationTest {
    private val validator = Validation.buildDefaultValidatorFactory().validator
    private fun valid() = ReunionRequest("Persona Prueba", "Coordinación", "Universidad", "Emprendimiento", "persona@example.com", nivel="Gold", privacidad=true, turnstileToken="token")
    @Test fun `correo fuera de edu ec es permitido`() { assertTrue(validator.validate(valid()).isEmpty()) }
    @Test fun `requiere consentimiento y campos obligatorios`() {
        val errors = validator.validate(valid().copy(nombre="", privacidad=false, correo="incorrecto", turnstileToken=""))
        assertTrue(errors.map { it.propertyPath.toString() }.containsAll(listOf("nombre","privacidad","correo","turnstileToken")))
    }
    @Test fun `rechaza niveles ajenos y mensaje excesivo`() {
        val errors = validator.validate(valid().copy(nivel="Inventado", mensaje="a".repeat(2001)))
        assertEquals(setOf("nivel","mensaje"), errors.map { it.propertyPath.toString() }.toSet())
    }
}

package com.checkbiz.backend.util

/**
 * Combina el país elegido en el registro con el número local de 10 dígitos
 * para guardar el teléfono completo en formato E.164 (sin el símbolo '+',
 * para que quepa en VARCHAR(15) — el '+' se agrega solo al llamar al
 * proveedor de SMS, nunca al guardar en la base).
 *
 * Debe coincidir exactamente con PAISES en
 * frontend/src/lib/registroValidation.js — si agregas un país allá,
 * agrégalo aquí también.
 */
object Telefonos {

    private val PREFIJOS = mapOf(
        "EC" to "593",
        "CO" to "57",
        "MX" to "52",
        "US" to "1"
    )

    /**
     * @param pais código de 2 letras (ya validado por RegistroRequest.pais)
     * @param telefono número local de 10 dígitos, tal como lo escribió el usuario
     * @return dígitos E.164 sin '+', ej: "593991234567"
     */
    fun combinar(pais: String, telefono: String): String {
        val prefijo = PREFIJOS[pais]
            ?: throw IllegalArgumentException("País no soportado: $pais")
        // En Ecuador y Colombia el celular se escribe con un '0' inicial que
        // se omite en el formato internacional (0991234567 -> 593991234567).
        val local = telefono.removePrefix("0")
        return prefijo + local
    }
}
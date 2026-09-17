package com.checkbiz.backend.util

/**
 * Validación de cédula ecuatoriana — Algoritmo Módulo 10 (Capa 1 de identidad).
 *
 * Reglas:
 *  - 10 dígitos numéricos.
 *  - Los 2 primeros dígitos son el código de provincia: 01–24, o 30 para
 *    ecuatorianos en el exterior.
 *  - El tercer dígito, para personas naturales, debe estar entre 0 y 5.
 *  - El décimo dígito es el verificador, calculado con coeficientes
 *    alternados [2,1,2,1,2,1,2,1,2] sobre los primeros 9 dígitos.
 *
 * Esta es solo la Capa 1 (estructura): confirma que el número es
 * matemáticamente válido, no que pertenece a esa persona — para eso están
 * las capas 2 (OTP), 3 (foto) y 4 (SENESCYT/SRI).
 */
data class ResultadoValidacion(val valida: Boolean, val motivo: String? = null)

object Modulo10 {

    private val COEFICIENTES = intArrayOf(2, 1, 2, 1, 2, 1, 2, 1, 2)

    fun validar(cedula: String): ResultadoValidacion {
        if (!Regex("^\\d{10}$").matches(cedula)) {
            return ResultadoValidacion(false, "Debe tener exactamente 10 dígitos numéricos")
        }

        val digitos = cedula.map { it - '0' }

        val provincia = cedula.substring(0, 2).toInt()
        val provinciaValida = (provincia in 1..24) || provincia == 30
        if (!provinciaValida) {
            return ResultadoValidacion(false, "El número de cédula ingresado no es válido")
        }

        val tercerDigito = digitos[2]
        if (tercerDigito > 5) {
            return ResultadoValidacion(false, "El número de cédula ingresado no es válido")
        }

        var suma = 0
        for (i in 0 until 9) {
            var producto = digitos[i] * COEFICIENTES[i]
            if (producto > 9) producto -= 9
            suma += producto
        }

        val decenaSuperior = Math.ceil(suma / 10.0).toInt() * 10
        var verificadorEsperado = decenaSuperior - suma
        if (verificadorEsperado == 10) verificadorEsperado = 0

        val verificadorReal = digitos[9]

        if (verificadorEsperado != verificadorReal) {
            return ResultadoValidacion(false, "El número de cédula ingresado no es válido")
        }

        return ResultadoValidacion(true)
    }

    /** Genera una cédula matemáticamente válida (útil para seeds/tests). */
    fun generarValida(provincia: Int = 17, tercerDigito: Int = 0): String {
        val prov = provincia.toString().padStart(2, '0')
        var base = prov + tercerDigito.toString()
        while (base.length < 9) {
            base += (0..9).random().toString()
        }
        val digitos = base.map { it - '0' }

        var suma = 0
        for (i in 0 until 9) {
            var producto = digitos[i] * COEFICIENTES[i]
            if (producto > 9) producto -= 9
            suma += producto
        }
        val decenaSuperior = Math.ceil(suma / 10.0).toInt() * 10
        var verificador = decenaSuperior - suma
        if (verificador == 10) verificador = 0

        return base + verificador.toString()
    }
}
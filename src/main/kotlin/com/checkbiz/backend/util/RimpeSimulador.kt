package com.checkbiz.backend.util

import com.checkbiz.backend.dto.SimulacionRimpeResponse
import java.math.BigDecimal

/**
 * Simulador RIMPE (B8) — calculadora pura, sin estado ni acceso a datos.
 *
 * Los umbrales ($20,000 y $300,000 de ingresos anuales) son los tramos del
 * régimen RIMPE ecuatoriano tal como los cita la propuesta original del
 * proyecto. La cuota fija de Negocio Popular ($60) es la misma cifra de
 * referencia de esa propuesta — el equipo de la revisión del 17 de sept ya
 * advirtió que este monto tributario NO fue validado contra una fuente
 * oficial vigente, así que esto es una estimación de orientación, nunca
 * asesoría tributaria: la respuesta siempre lleva una advertencia explícita
 * y remite a la Cámara de Impuestos / Consultorio Contable para el monto
 * exacto.
 */
object RimpeSimulador {

    private val LIMITE_NEGOCIO_POPULAR = BigDecimal("20000")
    private val LIMITE_RIMPE = BigDecimal("300000")
    private val CUOTA_NEGOCIO_POPULAR = BigDecimal("60.00")

    private const val ADVERTENCIA =
        "Estimación referencial para orientarte — no es asesoría tributaria. " +
            "Confirma el monto exacto y vigente con el SRI o tu Consultorio Contable Universitario."

    fun simular(ingresosAnuales: BigDecimal): SimulacionRimpeResponse = when {
        ingresosAnuales <= LIMITE_NEGOCIO_POPULAR -> SimulacionRimpeResponse(
            categoria = "Negocio Popular (RIMPE)",
            cuotaAnualEstimada = CUOTA_NEGOCIO_POPULAR,
            requiereFacturaElectronica = false,
            mensaje = "Con ingresos de hasta $20,000 al año calificas como Negocio Popular: pagas una " +
                "cuota fija anual y puedes emitir notas de venta en lugar de factura electrónica.",
            advertencia = ADVERTENCIA,
        )

        ingresosAnuales <= LIMITE_RIMPE -> SimulacionRimpeResponse(
            categoria = "Emprendedor (RIMPE)",
            cuotaAnualEstimada = null,
            requiereFacturaElectronica = true,
            mensaje = "Con ingresos entre $20,000 y $300,000 al año calificas como Emprendedor RIMPE: " +
                "la cuota depende de tu actividad económica y sí debes emitir factura electrónica.",
            advertencia = ADVERTENCIA,
        )

        else -> SimulacionRimpeResponse(
            categoria = "Régimen General",
            cuotaAnualEstimada = null,
            requiereFacturaElectronica = true,
            mensaje = "Con más de $300,000 al año ya no calificas para RIMPE — te corresponde el régimen general del SRI.",
            advertencia = ADVERTENCIA,
        )
    }
}

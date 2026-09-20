package com.checkbiz.backend.config

import com.checkbiz.backend.domain.CuentaInstitucional
import com.checkbiz.backend.repository.CuentaInstitucionalRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

/**
 * No existe todavía una pantalla para que el equipo de CheckBiz cree cuentas
 * institucionales a mano (E6, sin construir), así que — igual que con el
 * admin — se siembra una cuenta demo de la Cámara de Impuestos al arrancar,
 * lista para el pitch. Nunca toca una cuenta que ya exista.
 */
@Component
class InstitucionalSeeder(
    private val cuentaRepository: CuentaInstitucionalRepository,
    private val passwordEncoder: PasswordEncoder,
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(InstitucionalSeeder::class.java)

    companion object {
        const val CORREO_DEMO = "camara.impuestos@checkbiz.ec"
        const val PASSWORD_ARRANQUE = "CamaraImpuestos2025!"
    }

    override fun run(vararg args: String?) {
        if (cuentaRepository.findByCorreo(CORREO_DEMO).isPresent) return

        cuentaRepository.save(
            CuentaInstitucional(
                tipo = "camara_impuestos",
                nombreInstitucion = "Cámara de Impuestos (demo)",
                correo = CORREO_DEMO,
                passwordHash = passwordEncoder.encode(PASSWORD_ARRANQUE),
                activo = true,
            )
        )
        log.warn(
            "Cuenta institucional demo lista -> correo: {} · password: {} (cámbiala apenas puedas)",
            CORREO_DEMO, PASSWORD_ARRANQUE
        )
    }
}

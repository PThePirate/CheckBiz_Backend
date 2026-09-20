package com.checkbiz.backend.config

import com.checkbiz.backend.domain.CuentaInstitucional
import com.checkbiz.backend.domain.Universidad
import com.checkbiz.backend.repository.CuentaInstitucionalRepository
import com.checkbiz.backend.repository.UniversidadRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

/**
 * No existe todavía una pantalla para que el equipo de CheckBiz cree cuentas
 * institucionales a mano (E6, sin construir), así que — igual que con el
 * admin — se siembran cuentas demo al arrancar, listas para el pitch: una
 * Cámara de Impuestos (D) y una universidad (C), cada una con su propio
 * login. Nunca toca una cuenta que ya exista.
 */
@Component
class InstitucionalSeeder(
    private val cuentaRepository: CuentaInstitucionalRepository,
    private val universidadRepository: UniversidadRepository,
    private val passwordEncoder: PasswordEncoder,
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(InstitucionalSeeder::class.java)

    companion object {
        const val CORREO_DEMO = "camara.impuestos@checkbiz.ec"
        const val PASSWORD_ARRANQUE = "CamaraImpuestos2025!"

        const val CORREO_UNIVERSIDAD_DEMO = "universidad.demo@checkbiz.ec"
        const val PASSWORD_UNIVERSIDAD_DEMO = "UniversidadDemo2025!"
        const val NOMBRE_UNIVERSIDAD_DEMO = "Universidad de Guayaquil (demo)"
    }

    override fun run(vararg args: String?) {
        if (cuentaRepository.findByCorreo(CORREO_DEMO).isEmpty) {
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

        if (cuentaRepository.findByCorreo(CORREO_UNIVERSIDAD_DEMO).isEmpty) {
            val cuenta = cuentaRepository.save(
                CuentaInstitucional(
                    tipo = "universidad",
                    nombreInstitucion = NOMBRE_UNIVERSIDAD_DEMO,
                    correo = CORREO_UNIVERSIDAD_DEMO,
                    passwordHash = passwordEncoder.encode(PASSWORD_UNIVERSIDAD_DEMO),
                    activo = true,
                )
            )
            universidadRepository.save(Universidad(nombre = NOMBRE_UNIVERSIDAD_DEMO, cuenta = cuenta, activa = true))
            log.warn(
                "Cuenta institucional demo lista -> correo: {} · password: {} (cámbiala apenas puedas)",
                CORREO_UNIVERSIDAD_DEMO, PASSWORD_UNIVERSIDAD_DEMO
            )
        }
    }
}

package com.checkbiz.backend.config

import com.checkbiz.backend.repository.AdminRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

/**
 * db/checkbiz_schema.sql crea un admin de arranque con un hash placeholder
 * ('<reemplazar_por_hash_bcrypt>') porque el SQL puro no puede generar un
 * hash de bcrypt. Este seeder, al arrancar, detecta ese placeholder exacto
 * y lo reemplaza por un hash real — nunca toca una contraseña que ya fue
 * cambiada legítimamente (deja de coincidir con el placeholder).
 */
@Component
class AdminSeeder(
    private val adminRepository: AdminRepository,
    private val passwordEncoder: PasswordEncoder,
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(AdminSeeder::class.java)

    companion object {
        const val PLACEHOLDER = "<reemplazar_por_hash_bcrypt>"
        const val CORREO_ADMIN = "admin@checkbiz.ec"
        const val PASSWORD_ARRANQUE = "CheckBizAdmin2025!"
    }

    override fun run(vararg args: String?) {
        val admin = adminRepository.findByCorreo(CORREO_ADMIN).orElse(null) ?: return

        if (admin.passwordHash == PLACEHOLDER) {
            admin.passwordHash = passwordEncoder.encode(PASSWORD_ARRANQUE)
            adminRepository.save(admin)
            log.warn(
                "Admin de arranque listo -> correo: {} · password: {} (cámbiala apenas puedas)",
                CORREO_ADMIN, PASSWORD_ARRANQUE
            )
        }
    }
}

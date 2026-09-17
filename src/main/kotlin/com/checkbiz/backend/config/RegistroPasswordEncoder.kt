package com.checkbiz.backend.config

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder

fun registroPasswordEncoder(): PasswordEncoder {
    val bcrypt = BCryptPasswordEncoder(12)
    return DelegatingPasswordEncoder(
        "pbkdf2@SpringSecurity_v5_8",
        mapOf(
            "pbkdf2@SpringSecurity_v5_8" to Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
            "bcrypt" to bcrypt,
        ),
    ).apply { setDefaultPasswordEncoderForMatches(bcrypt) }
}

package com.checkbiz.backend.config

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Tipos de titular de un token, para que cada middleware exija el tipo
 * correcto (un token de admin nunca debe servir como token de usuario).
 */
enum class TipoToken { USUARIO, ADMIN, INSTITUCIONAL }

data class UsuarioClaims(
    val sub: UUID,
    val rolCliente: Boolean,
    val rolEmprendedor: Boolean,
    val kycLayer: Short,
)

data class AdminClaims(val sub: UUID, val rol: String)

/** tipo: 'universidad' | 'camara_impuestos' | 'camara_negocio' (ver cuentas_institucionales.tipo). */
data class InstitucionalClaims(val sub: UUID, val tipo: String, val nombreInstitucion: String)

@Component
class JwtService(
    @Value("\${checkbiz.jwt.secret}") secret: String,
    @Value("\${checkbiz.jwt.expiration-ms}") private val expirationMs: Long,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8).let {
        // HS256 exige >= 32 bytes; si el secreto configurado es corto (p. ej.
        // en un .env de ejemplo), lo repetimos para no romper en arranque.
        if (it.size >= 32) it else ByteArray(32) { i -> it[i % it.size] }
    })

    fun generarTokenUsuario(claims: UsuarioClaims): String =
        Jwts.builder()
            .subject(claims.sub.toString())
            .claim("tipo", TipoToken.USUARIO.name)
            .claim("rolCliente", claims.rolCliente)
            .claim("rolEmprendedor", claims.rolEmprendedor)
            .claim("kycLayer", claims.kycLayer.toInt())
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expirationMs))
            .signWith(key)
            .compact()

    fun generarTokenAdmin(claims: AdminClaims): String =
        Jwts.builder()
            .subject(claims.sub.toString())
            .claim("tipo", TipoToken.ADMIN.name)
            .claim("rol", claims.rol)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expirationMs))
            .signWith(key)
            .compact()

    fun generarTokenInstitucional(claims: InstitucionalClaims): String =
        Jwts.builder()
            .subject(claims.sub.toString())
            .claim("tipo", TipoToken.INSTITUCIONAL.name)
            .claim("tipoInstitucion", claims.tipo)
            .claim("nombreInstitucion", claims.nombreInstitucion)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expirationMs))
            .signWith(key)
            .compact()

    /** Lanza JwtException si el token es inválido o expiró (lo captura el filtro). */
    fun parsearClaims(token: String): Claims =
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload

    fun tipoDe(claims: Claims): TipoToken = TipoToken.valueOf(claims.get("tipo", String::class.java))
}

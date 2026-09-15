package com.checkbiz.backend.config

import com.checkbiz.backend.exception.ErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class JwtAuthFilter(
    private val jwtService: JwtService,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val token = header.removePrefix("Bearer ").trim()

        try {
            val claims = jwtService.parsearClaims(token)
            val sub = UUID.fromString(claims.subject)

            val auth = when (jwtService.tipoDe(claims)) {
                TipoToken.USUARIO -> CheckBizAuthenticationToken(
                    tipo = TipoToken.USUARIO,
                    principalObj = UsuarioClaims(
                        sub = sub,
                        rolCliente = claims.get("rolCliente", Boolean::class.java) ?: false,
                        rolEmprendedor = claims.get("rolEmprendedor", Boolean::class.java) ?: false,
                        kycLayer = (claims.get("kycLayer", Integer::class.java) ?: 1).toShort(),
                    ),
                    authorities = listOf(SimpleGrantedAuthority("ROLE_USUARIO")),
                )
                TipoToken.ADMIN -> CheckBizAuthenticationToken(
                    tipo = TipoToken.ADMIN,
                    principalObj = AdminClaims(sub = sub, rol = claims.get("rol", String::class.java) ?: "admin"),
                    authorities = listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
                )
            }

            SecurityContextHolder.getContext().authentication = auth
            filterChain.doFilter(request, response)
        } catch (ex: JwtException) {
            responderNoAutorizado(response, "Token inválido o expirado")
        } catch (ex: IllegalArgumentException) {
            responderNoAutorizado(response, "Token malformado")
        }
    }

    private fun responderNoAutorizado(response: HttpServletResponse, mensaje: String) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(
            objectMapper.writeValueAsString(
                ErrorResponse(error = "TOKEN_INVALIDO", mensaje = mensaje)
            )
        )
    }
}

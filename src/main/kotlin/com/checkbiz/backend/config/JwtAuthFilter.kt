package com.checkbiz.backend.config

import com.checkbiz.backend.exception.ErrorResponse
import com.checkbiz.backend.repository.UsuarioRepository
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
    private val usuarioRepository: UsuarioRepository,
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
                TipoToken.USUARIO -> {
                    // El veto (E4) debe cortar el acceso de inmediato, no
                    // solo bloquear registro/login nuevos — sin esta
                    // consulta, un JWT firmado antes del veto seguía
                    // funcionando hasta que expiraba (hasta 7 días).
                    val estadoCedula = usuarioRepository.estadoCedulaDe(sub)
                    if (estadoCedula == null || estadoCedula == "vetada") {
                        responderNoAutorizado(response, "Esta cuenta ya no tiene acceso")
                        return
                    }
                    CheckBizAuthenticationToken(
                        tipo = TipoToken.USUARIO,
                        principalObj = UsuarioClaims(
                            sub = sub,
                            rolCliente = claims.get("rolCliente", Boolean::class.javaObjectType) ?: false,
                            rolEmprendedor = claims.get("rolEmprendedor", Boolean::class.javaObjectType) ?: false,
                            kycLayer = (claims.get("kycLayer", Int::class.javaObjectType) ?: 1).toShort(),
                        ),
                        authorities = listOf(SimpleGrantedAuthority("ROLE_USUARIO")),
                    )
                }
                TipoToken.ADMIN -> CheckBizAuthenticationToken(
                    tipo = TipoToken.ADMIN,
                    principalObj = AdminClaims(sub = sub, rol = claims.get("rol", String::class.java) ?: "admin"),
                    authorities = listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
                )
                TipoToken.INSTITUCIONAL -> CheckBizAuthenticationToken(
                    tipo = TipoToken.INSTITUCIONAL,
                    principalObj = InstitucionalClaims(
                        sub = sub,
                        tipo = claims.get("tipoInstitucion", String::class.java) ?: "",
                        nombreInstitucion = claims.get("nombreInstitucion", String::class.java) ?: "",
                    ),
                    authorities = listOf(SimpleGrantedAuthority("ROLE_INSTITUCIONAL")),
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

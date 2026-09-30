package com.checkbiz.backend.config

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * Authentication de Spring Security que carga el UUID del titular + su
 * "principal" tipado (UsuarioClaims o AdminClaims), para que los
 * controladores puedan leerlo directo sin volver a parsear el JWT.
 */
class CheckBizAuthenticationToken(
    val tipo: TipoToken,
    private val principalObj: Any,
    authorities: Collection<SimpleGrantedAuthority>,
) : AbstractAuthenticationToken(authorities) {

    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any? = null
    override fun getPrincipal(): Any = principalObj
}

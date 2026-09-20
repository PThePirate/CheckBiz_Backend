package com.checkbiz.backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import com.fasterxml.jackson.databind.ObjectMapper
import com.checkbiz.backend.exception.ErrorResponse

@Configuration
class SecurityConfig(private val jwtAuthFilter: JwtAuthFilter, private val objectMapper: ObjectMapper) {

    // PBKDF2 admite las contraseñas nuevas de 80 caracteres sin el límite de
    // 72 bytes de BCrypt. Las cuentas existentes conservan su hash BCrypt.
    @Bean
    fun passwordEncoder(): PasswordEncoder = registroPasswordEncoder()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        // En producción: restringir 'allowedOrigins' al dominio real del frontend.
        val config = CorsConfiguration().apply {
            allowedOriginPatterns = listOf("*")
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
        }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", config) }
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() } // API stateless con JWT — no hay sesión de navegador que proteger
            .cors { }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { errors ->
                errors.authenticationEntryPoint { _, response, _ ->
                    response.status = 401
                    response.contentType = "application/json;charset=UTF-8"
                    objectMapper.writeValue(response.writer, ErrorResponse(error = "NO_AUTENTICADO", mensaje = "Inicia sesión para continuar con la verificación."))
                }
                errors.accessDeniedHandler { _, response, _ ->
                    response.status = 403
                    response.contentType = "application/json;charset=UTF-8"
                    objectMapper.writeValue(response.writer, ErrorResponse(error = "ACCESO_DENEGADO", mensaje = "Tu cuenta no tiene acceso a esta acción."))
                }
            }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/api/salud").permitAll()
                    .requestMatchers("/api/negocios/**").permitAll()
                    .requestMatchers(
                        "/api/auth/registro",
                        "/api/auth/login",
                        "/api/admin/login",
                        "/api/institucional/login",
                    ).permitAll()
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .requestMatchers("/api/institucional/**").hasRole("INSTITUCIONAL")
                    .requestMatchers("/api/auth/**").hasRole("USUARIO")
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}

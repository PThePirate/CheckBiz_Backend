package com.checkbiz.backend.controller

import com.checkbiz.backend.config.AdminClaims
import com.checkbiz.backend.config.CheckBizAuthenticationToken
import com.checkbiz.backend.dto.*
import com.checkbiz.backend.service.AdminService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/admin")
class AdminController(private val adminService: AdminService) {

    private fun adminActual(): AdminClaims {
        val auth = SecurityContextHolder.getContext().authentication as CheckBizAuthenticationToken
        return auth.principal as AdminClaims
    }

    // Ruta separada del login público — nunca en el mismo formulario que usuarios.
    @PostMapping("/login")
    fun login(@Valid @RequestBody req: AdminLoginRequest): AdminAuthResponse = adminService.login(req)

    // --- KYC (E2) ---
    @GetMapping("/kyc/fotos")
    fun listarFotos(@RequestParam(required = false, defaultValue = "en_revision") estado: String): ColaFotosResponse =
        adminService.listarFotosPendientes(estado)

    @PatchMapping("/kyc/fotos/{id}")
    fun decidirFoto(
        @PathVariable id: UUID,
        @Valid @RequestBody req: DecisionFotoRequest,
    ): VerificacionFotoResponse = adminService.decidirFoto(id, adminActual().sub, req)

    // Archivo real de la foto (selfie + cédula). Antes vivía en
    // /uploads/**, servido públicamente sin autenticación; ahora exige
    // rol ADMIN (ya cubierto por /api/admin/** en SecurityConfig).
    @GetMapping("/kyc/fotos/{id}/archivo")
    fun obtenerArchivoFoto(@PathVariable id: UUID): ResponseEntity<ByteArray> {
        val (bytes, tipo) = adminService.obtenerArchivoFoto(id)
        return ResponseEntity.ok().contentType(tipo).body(bytes)
    }

    // --- Veto por cédula (E4) ---
    @PostMapping("/veto")
    @ResponseStatus(HttpStatus.CREATED)
    fun vetar(@Valid @RequestBody req: VetoRequest): Map<String, Any> {
        val veto = adminService.vetarCedula(adminActual().sub, req)
        return mapOf("mensaje" to "Cédula vetada permanentemente", "veto" to veto)
    }

    // --- Panel general: estadísticas y actividad ---
    @GetMapping("/estadisticas")
    fun estadisticas(): EstadisticasResponse = adminService.estadisticas()

    @GetMapping("/actividad")
    fun actividad(@RequestParam(required = false, defaultValue = "6") limite: Int): List<ActividadItemResponse> =
        adminService.actividadReciente(limite)

    // --- Búsqueda de usuarios + ficha 360° ---
    @GetMapping("/usuarios")
    fun buscarUsuarios(@RequestParam(required = false, defaultValue = "") buscar: String): List<UsuarioResumenResponse> =
        adminService.buscarUsuarios(buscar)

    @GetMapping("/usuarios/{id}")
    fun obtenerUsuario(@PathVariable id: UUID): UsuarioDetalleResponse = adminService.obtenerUsuarioDetalle(id)

    // --- Categorías del catálogo maestro (E5) ---
    @GetMapping("/categorias")
    fun listarCategorias(): List<CategoriaResponse> = adminService.listarCategorias()

    @PostMapping("/categorias")
    @ResponseStatus(HttpStatus.CREATED)
    fun crearCategoria(@Valid @RequestBody req: CrearCategoriaRequest): CategoriaResponse =
        adminService.crearCategoria(req)

    @PatchMapping("/categorias/{id}")
    fun alternarCategoria(@PathVariable id: Int): CategoriaResponse = adminService.alternarCategoria(id)

    // --- Denuncias (E3) ---
    @GetMapping("/denuncias")
    fun listarDenuncias(@RequestParam(required = false, defaultValue = "abierta") estado: String): List<DenunciaResponse> =
        adminService.listarDenuncias(estado)

    @PatchMapping("/denuncias/{id}")
    fun resolverDenuncia(
        @PathVariable id: UUID,
        @Valid @RequestBody req: ResolverDenunciaRequest,
    ): DenunciaResponse = adminService.resolverDenuncia(id, adminActual().sub, req)

    // --- Logs de auditoría (E8) ---
    @GetMapping("/logs")
    fun listarLogs(): List<LogAuditoriaResponse> = adminService.listarLogs()
}
package com.checkbiz.backend.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class SaludController {
    @GetMapping("/salud")
    fun salud(): Map<String, Any> = mapOf("ok" to true, "servicio" to "checkbiz-backend")
}

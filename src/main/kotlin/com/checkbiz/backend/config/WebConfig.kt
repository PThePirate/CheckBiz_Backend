package com.checkbiz.backend.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.io.File

@Configuration
class WebConfig(
    @Value("\${checkbiz.uploads.dir}") private val uploadsDir: String,
) : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        // Las fotos de verificación (Capa 3) se sirven como estáticos en
        // desarrollo. En producción esto lo serviría el bucket de
        // almacenamiento, no la propia app.
        val ruta = File(uploadsDir).absolutePath
        registry.addResourceHandler("/uploads/**").addResourceLocations("file:$ruta/")
    }
}

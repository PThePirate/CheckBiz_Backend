package com.checkbiz.backend.repository

import com.checkbiz.backend.domain.Negocio
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface NegocioRepository : JpaRepository<Negocio, UUID> {
    fun countByEstadoPublicacion(estado: String): Long
    fun countByCategoriaId(categoriaId: Int): Long
    fun findByUsuarioId(usuarioId: UUID): Negocio?
    fun existsBySlug(slug: String): Boolean
    fun findBySlugAndEstadoPublicacion(slug: String, estado: String): Negocio?

    /**
     * Búsqueda pública (A4/A5). Cada filtro es opcional — si viene null,
     * la condición se ignora (patrón ": p IS NULL OR ..." de JPQL). Solo
     * devuelve negocios con estado_publicacion = 'publicado'.
     */
    @Query(
        """
        SELECT n FROM Negocio n
        WHERE n.estadoPublicacion = 'publicado'
          AND (:categoriaId IS NULL OR n.categoria.id = :categoriaId)
          AND (:ciudad IS NULL OR LOWER(n.ciudad) = LOWER(:ciudad))
          AND (:nivel IS NULL OR n.nivelFormalizacion = :nivel)
          AND (
                :texto IS NULL
                OR LOWER(n.nombreComercial) LIKE LOWER(CONCAT('%', :texto, '%'))
                OR LOWER(n.descripcionCorta) LIKE LOWER(CONCAT('%', :texto, '%'))
              )
        ORDER BY n.trustScore DESC, n.creadoEn DESC
        """
    )
    fun buscarPublicados(
        @Param("categoriaId") categoriaId: Int?,
        @Param("ciudad") ciudad: String?,
        @Param("nivel") nivel: String?,
        @Param("texto") texto: String?,
    ): List<Negocio>

    @Query(
        "SELECT DISTINCT n.ciudad FROM Negocio n WHERE n.estadoPublicacion = 'publicado' AND n.ciudad IS NOT NULL ORDER BY n.ciudad ASC"
    )
    fun ciudadesDisponibles(): List<String>
}
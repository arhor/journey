package com.github.arhor.journey.data.repository

import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.repository.MapStylesRepository
import jakarta.inject.Inject
import javax.inject.Singleton

@Singleton
class MapStylesRepositoryImpl @Inject constructor() : MapStylesRepository {

    override fun findAll(): List<MapStyle> = DEFAULT_STYLES.values.toList()

    override fun findById(id: String): MapStyle? = DEFAULT_STYLES[id]

    override fun existsById(id: String): Boolean = id in DEFAULT_STYLES

    /* ------------------------------------------ Internal implementation ------------------------------------------- */

    private companion object {
        private val MAP_STYLE_VOYAGER = MapStyle.remote(
            id = "voyager-gl-style",
            name = "Voyager",
            value = "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json",
        )
        private val MAP_STYLE_POSITRON = MapStyle.remote(
            id = "positron-gl-style",
            name = "Positron",
            value = "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json",
        )
        private val MAP_STYLE_DARK_MATTER = MapStyle.remote(
            id = "dark-matter-gl-style",
            name = "Dark Matter",
            value = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json",
        )

        val DEFAULT_STYLES = listOf(
            MAP_STYLE_VOYAGER,
            MAP_STYLE_POSITRON,
            MAP_STYLE_DARK_MATTER,
        ).associateBy { it.id }
    }
}

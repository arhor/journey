package com.github.arhor.journey.domain.model

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test

class MapStyleTest {

    @Test
    fun `availableStyles should expose bundled map styles with light as default`() {
        // Given
        val expectedStyles = listOf(
            MapStyle.bundle(
                id = "light",
                name = "Light",
                value = "asset://map/styles/light.json",
            ),
            MapStyle.bundle(
                id = "cyberpunk",
                name = "Cyberpunk",
                value = "asset://map/styles/cyberpunk.json",
            ),
            MapStyle.bundle(
                id = "urban-noir",
                name = "Urban Noir",
                value = "asset://map/styles/urban-noir.json",
            ),
        )

        // When
        val actual = MapStyle.availableStyles

        // Then
        actual shouldContainExactly expectedStyles
        MapStyle.defaultStyle shouldBe expectedStyles.first()
    }

    @Test
    fun `styleById should resolve known style and fallback to null when id is unknown`() {
        // When
        val knownStyle = MapStyle.styleById("light")
        val unknownStyle = MapStyle.styleById("unknown")

        // Then
        knownStyle shouldBe MapStyle.bundle(
            id = "light",
            name = "Light",
            value = "asset://map/styles/light.json",
        )
        unknownStyle shouldBe null
    }
}

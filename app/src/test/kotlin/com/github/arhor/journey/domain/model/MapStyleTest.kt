package com.github.arhor.journey.domain.model

import io.kotest.matchers.shouldBe
import org.junit.Test

class MapStyleTest {

    @Test
    fun `default id should remain stable`() {
        // Given
        val defaultId = MapStyle.DEFAULT_ID

        // When
        val style = MapStyle(id = defaultId, name = "Default")

        // Then
        style.id shouldBe "default"
    }
}

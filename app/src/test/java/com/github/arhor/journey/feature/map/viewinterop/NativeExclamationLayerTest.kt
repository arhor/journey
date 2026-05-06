package com.github.arhor.journey.feature.map.viewinterop

import io.kotest.matchers.shouldBe
import org.junit.Test

class NativeExclamationLayerTest {

    @Test
    fun `layerId should return native exclamation layer id`() {
        // Given

        // When
        val actual = NativeExclamationLayer.layerId()

        // Then
        actual shouldBe "native-exclamation-layer"
    }
}

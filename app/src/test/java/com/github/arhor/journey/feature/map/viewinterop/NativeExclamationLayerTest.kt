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

    @Test
    fun `addToWithManagedContext should destroy context and rethrow when add layer throws after context creation`() {
        // Given
        val createdContext = 42L
        val expected = IllegalStateException("add-layer-failed")
        val destroyedContexts = mutableListOf<Long>()
        var repaintCalls = 0

        // When
        val actual = try {
            NativeExclamationLayer.addToWithManagedContext(
                createContext = { createdContext },
                destroyContext = { context -> destroyedContexts += context },
                addLayer = { throw expected },
                repaint = { repaintCalls += 1 },
            )
            null
        } catch (throwable: Throwable) {
            throwable
        }

        // Then
        actual shouldBe expected
        destroyedContexts shouldBe listOf(createdContext)
        repaintCalls shouldBe 0
    }
}

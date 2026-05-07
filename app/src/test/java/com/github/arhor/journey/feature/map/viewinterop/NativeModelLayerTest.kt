package com.github.arhor.journey.feature.map.viewinterop

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import org.junit.Test

class NativeModelLayerTest {

    @Test
    fun `layerId should return native model layer id`() {
        // Given

        // When
        val actual = NativeModelLayer.layerId()

        // Then
        actual shouldBe "native-model-layer"
    }

    @Test
    fun `addToWithManagedContext should repaint when layer is added`() {
        // Given
        val createdContext = 42L
        val addedContexts = mutableListOf<Long>()
        val destroyedContexts = mutableListOf<Long>()
        var repaintCalls = 0

        // When
        NativeModelLayer.addToWithManagedContext(
            createContext = { createdContext },
            destroyContext = { context -> destroyedContexts += context },
            addLayer = { context -> addedContexts += context },
            repaint = { repaintCalls += 1 },
        )

        // Then
        addedContexts shouldBe listOf(createdContext)
        destroyedContexts shouldBe emptyList()
        repaintCalls shouldBe 1
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
            NativeModelLayer.addToWithManagedContext(
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

    @Test
    fun `native model renderer should use MapLibre projection matrix instead of manual camera rotation`() {
        // Given
        val source = File("src/main/cpp/lib/layers/model/ModelLayer.cpp").readText()

        // When
        val usesMapLibreProjectionMatrix = source.contains("params.projectionMatrix")

        // Then
        usesMapLibreProjectionMatrix shouldBe true
        source shouldContain "u_projection_matrix"
        source shouldContain "std::pow(2.0, params.zoom)"
        source shouldContain "worldPixelsPerMeter"
        source shouldNotContain "projectMetersOffsetToNdc"
    }
}

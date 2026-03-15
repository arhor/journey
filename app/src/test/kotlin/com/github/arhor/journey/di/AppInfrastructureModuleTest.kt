package com.github.arhor.journey.di

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Job
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.Test
import java.time.ZoneOffset

class AppInfrastructureModuleTest {

    @Test
    fun `provideJson should decode payload when unknown keys are present`() {
        // Given
        val json = AppInfrastructureModule.provideJson()
        val payload = """{"id":42,"unexpected":"value"}"""

        // When
        val decoded = json.decodeFromString<QuestPayload>(payload)

        // Then
        decoded shouldBe QuestPayload(id = 42)
    }

    @Test
    fun `provideJson should fail decoding when required field is missing`() {
        // Given
        val json = AppInfrastructureModule.provideJson()
        val payload = """{"unexpected":"value"}"""

        // When
        val exception = shouldThrow<MissingFieldException> {
            json.decodeFromString<RequiredPayload>(payload)
        }

        // Then
        exception.message?.contains("value") shouldBe true
    }

    @Test
    fun `provideJson should use type discriminator when encoding sealed payload`() {
        // Given
        val json = AppInfrastructureModule.provideJson()
        val payload: MapEvent = MapEvent.Arrived(location = "forest")

        // When
        val encoded = json.encodeToString(payload)
        val decoded = json.decodeFromString<MapEvent>(encoded)

        // Then
        encoded.contains("\"type\":\"arrived\"") shouldBe true
        decoded shouldBe payload
    }

    @Test
    fun `provideAppCoroutineScope should keep parent active when one child fails`() {
        // Given
        val scope = AppInfrastructureModule.provideAppCoroutineScope()
        val parentJob = scope.coroutineContext[Job] ?: error("Expected Job in app scope")
        val failingChild = Job(parentJob)
        val siblingChild = Job(parentJob)

        // When
        failingChild.completeExceptionally(IllegalStateException("boom"))

        // Then
        parentJob.isCancelled shouldBe false
        siblingChild.isCancelled shouldBe false
        parentJob.cancel()
    }

    @Test
    fun `provideAppCoroutineScope should cancel child when parent is cancelled`() {
        // Given
        val scope = AppInfrastructureModule.provideAppCoroutineScope()
        val parentJob = scope.coroutineContext[Job] ?: error("Expected Job in app scope")
        val childJob = Job(parentJob)

        // When
        parentJob.cancel()

        // Then
        childJob.isCancelled shouldBe true
    }

    @Test
    fun `provideClock should return utc clock`() {
        // Given
        val expectedZone = ZoneOffset.UTC

        // When
        val clock = AppInfrastructureModule.provideClock()

        // Then
        clock.zone shouldBe expectedZone
    }

    @Serializable
    private data class QuestPayload(
        val id: Int,
    )

    @Serializable
    private data class RequiredPayload(
        val value: String,
    )

    @Serializable
    private sealed class MapEvent {
        @Serializable
        @SerialName("arrived")
        data class Arrived(
            val location: String,
        ) : MapEvent()
    }
}

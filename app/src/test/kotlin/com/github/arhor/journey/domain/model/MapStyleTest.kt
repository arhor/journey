package com.github.arhor.journey.domain.model

import io.kotest.matchers.shouldBe
import org.junit.Test

class MapStyleTest {

    @Test
    fun `fromName should return matching enum when stored value is valid`() {
        // Given
        val storedValue = "DARK"

        // When
        val style = MapStyle.fromName(storedValue)

        // Then
        style shouldBe MapStyle.DARK
    }

    @Test
    fun `fromName should return default when stored value is invalid`() {
        // Given
        val storedValue = "BROKEN_STYLE"

        // When
        val style = MapStyle.fromName(storedValue)

        // Then
        style shouldBe MapStyle.DEFAULT
    }
}

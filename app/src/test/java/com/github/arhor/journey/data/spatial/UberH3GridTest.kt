package com.github.arhor.journey.data.spatial

import com.github.arhor.journey.domain.model.GeoPoint
import com.uber.h3core.H3Core
import com.uber.h3core.util.LatLng
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class UberH3GridTest {

    @Test
    fun `cellId should return stable address when location and resolution are fixed`() {
        // Given
        val subject = UberH3Grid(
            h3 = mockk<H3Core>().apply {
                every { latLngToCellAddress(50.4501, 30.5234, 9) } returns "cell-kyiv"
            },
        )

        // When
        val actual = subject.cellId(lat = 50.4501, lon = 30.5234, resolution = 9)

        // Then
        actual shouldBe subject.cellId(lat = 50.4501, lon = 30.5234, resolution = 9)
    }

    @Test
    fun `gridDisk should include origin cell when radius is zero`() {
        // Given
        val subject = UberH3Grid(
            h3 = mockk<H3Core>().apply {
                every { latLngToCellAddress(50.4501, 30.5234, 9) } returns "cell-kyiv"
                every { gridDisk("cell-kyiv", 0) } returns listOf("cell-kyiv")
            },
        )
        val origin = subject.cellId(lat = 50.4501, lon = 30.5234, resolution = 9)

        // When
        val actual = subject.gridDisk(origin, radius = 0)

        // Then
        actual shouldBe listOf(origin)
    }

    @Test
    fun `gridDisk should include origin cell when radius is negative`() {
        // Given
        val subject = UberH3Grid(
            h3 = mockk<H3Core>().apply {
                every { latLngToCellAddress(50.4501, 30.5234, 9) } returns "cell-kyiv"
                every { gridDisk("cell-kyiv", 0) } returns listOf("cell-kyiv")
            },
        )
        val origin = subject.cellId(lat = 50.4501, lon = 30.5234, resolution = 9)

        // When
        val actual = subject.gridDisk(origin, radius = -1)

        // Then
        actual shouldBe listOf(origin)
    }

    @Test
    fun `cellCenter should map h3 lat lng to geo point when center is requested`() {
        // Given
        val subject = UberH3Grid(
            h3 = mockk<H3Core>().apply {
                every { cellToLatLng("cell-kyiv") } returns LatLng(50.4501, 30.5234)
            },
        )

        // When
        val actual = subject.cellCenter("cell-kyiv")

        // Then
        actual shouldBe GeoPoint(lat = 50.4501, lon = 30.5234)
    }
}

package com.github.arhor.journey.data.spatial

import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import com.uber.h3core.H3Core
import com.uber.h3core.PolygonToCellsFlags
import com.uber.h3core.util.LatLng
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class UberH3GridTest {

    @Test
    fun `cellId should delegate with lat then lon when location and resolution are fixed`() {
        // Given
        val subject = UberH3Grid(
            h3 = mockk<H3Core>().apply {
                every { latLngToCellAddress(50.4501, 30.5234, 9) } returns "cell-kyiv"
                every { latLngToCellAddress(30.5234, 50.4501, 9) } returns "cell-swapped"
            },
        )

        // When
        val actual = subject.cellId(lat = 50.4501, lon = 30.5234, resolution = 9)

        // Then
        actual shouldBe "cell-kyiv"
    }

    @Test
    fun `cellId should call h3 using lat lon ordering when converting a location`() {
        // Given
        val h3 = mockk<H3Core>().apply {
            every { latLngToCellAddress(50.4501, 30.5234, 9) } returns "cell-kyiv"
        }
        val subject = UberH3Grid(h3 = h3)

        // When
        subject.cellId(lat = 50.4501, lon = 30.5234, resolution = 9)

        // Then
        verify(exactly = 1) { h3.latLngToCellAddress(50.4501, 30.5234, 9) }
        verify(exactly = 0) { h3.latLngToCellAddress(30.5234, 50.4501, 9) }
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

    @Test
    fun `cellsInBounds should use overlapping polygon fill for bounds coverage`() {
        // Given
        val bounds = GeoBounds(
            south = 50.44,
            west = 30.51,
            north = 50.46,
            east = 30.53,
        )
        val h3 = mockk<H3Core>().apply {
            every {
                polygonToCellAddressesExperimental(
                    any(),
                    null,
                    9,
                    PolygonToCellsFlags.containment_overlapping,
                )
            } returns listOf("cell-b", "cell-a", "cell-a")
        }
        val subject = UberH3Grid(h3 = h3)

        // When
        val actual = subject.cellsInBounds(bounds = bounds, resolution = 9)

        // Then
        actual shouldBe listOf("cell-a", "cell-b")
        verify(exactly = 1) {
            h3.polygonToCellAddressesExperimental(
                listOf(
                    LatLng(50.44, 30.51),
                    LatLng(50.44, 30.53),
                    LatLng(50.46, 30.53),
                    LatLng(50.46, 30.51),
                ),
                null,
                9,
                PolygonToCellsFlags.containment_overlapping,
            )
        }
    }
}

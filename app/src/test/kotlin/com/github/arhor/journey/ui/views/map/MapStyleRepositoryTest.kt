package com.github.arhor.journey.ui.views.map

import com.github.arhor.journey.data.mapstyle.BundledMapStyleDataSource
import com.github.arhor.journey.data.mapstyle.MapStyleRecord
import com.github.arhor.journey.data.mapstyle.MapStyleResolver
import com.github.arhor.journey.data.mapstyle.MapStyleSelectionLocalDataSource
import com.github.arhor.journey.data.mapstyle.RemoteMapStyleLocalDataSource
import com.github.arhor.journey.data.mapstyle.RemoteMapStyleRemoteDataSource
import com.github.arhor.journey.data.repository.MapStyleRepositoryImpl
import com.github.arhor.journey.domain.model.MapResolvedStyle
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapStyleRepositoryTest {

    @Test
    fun `observeAvailableStyles should merge bundled and cached remote styles without duplicates`() = runTest {
        // Given
        val bundled = mockk<BundledMapStyleDataSource>()
        val remoteLocal = mockk<RemoteMapStyleLocalDataSource>()
        val remoteRemote = mockk<RemoteMapStyleRemoteDataSource>()
        val selectionLocal = mockk<MapStyleSelectionLocalDataSource>()
        val resolver = mockk<MapStyleResolver>()

        every { bundled.getStyles() } returns listOf(
            MapStyleRecord("default", "Default", MapStyleRecord.Source.BUNDLE, assetPath = "default.json", fallbackUri = "fallback"),
        )
        every { remoteLocal.observeCachedStyles() } returns MutableStateFlow(
            listOf(
                MapStyleRecord("default", "Default override", MapStyleRecord.Source.REMOTE, uri = "x"),
                MapStyleRecord("dark", "Dark", MapStyleRecord.Source.REMOTE, uri = "dark"),
            ),
        )
        every { selectionLocal.observeSelectedStyleId(any()) } returns MutableStateFlow("default")
        every { resolver.resolve(any()) } returns MapResolvedStyle.Uri("x")

        val repository = MapStyleRepositoryImpl(bundled, remoteLocal, remoteRemote, selectionLocal, resolver)

        // When
        val styles = repository.observeAvailableStyles().first()

        // Then
        styles.map { it.id } shouldBe listOf("default", "dark")
    }

    @Test
    fun `selectStyle should persist default when requested style is unavailable`() = runTest {
        // Given
        val bundled = mockk<BundledMapStyleDataSource>()
        val remoteLocal = mockk<RemoteMapStyleLocalDataSource>()
        val remoteRemote = mockk<RemoteMapStyleRemoteDataSource>()
        val selectionLocal = mockk<MapStyleSelectionLocalDataSource>()
        val resolver = mockk<MapStyleResolver>()

        every { bundled.getStyles() } returns listOf(
            MapStyleRecord("default", "Default", MapStyleRecord.Source.BUNDLE, assetPath = "default.json", fallbackUri = "fallback"),
        )
        every { remoteLocal.observeCachedStyles() } returns MutableStateFlow(emptyList())
        every { selectionLocal.observeSelectedStyleId(any()) } returns MutableStateFlow("default")
        every { resolver.resolve(any()) } returns MapResolvedStyle.Uri("x")
        coEvery { selectionLocal.setSelectedStyleId(any()) } returns Unit

        val repository = MapStyleRepositoryImpl(bundled, remoteLocal, remoteRemote, selectionLocal, resolver)

        // When
        repository.selectStyle("missing")

        // Then
        coVerify { selectionLocal.setSelectedStyleId("default") }
    }

    @Test
    fun `refreshRemoteStyles should not alter existing observation when refresh fails`() = runTest {
        // Given
        val bundled = mockk<BundledMapStyleDataSource>()
        val remoteLocal = mockk<RemoteMapStyleLocalDataSource>()
        val remoteRemote = mockk<RemoteMapStyleRemoteDataSource>()
        val selectionLocal = mockk<MapStyleSelectionLocalDataSource>()
        val resolver = mockk<MapStyleResolver>()

        every { bundled.getStyles() } returns listOf(
            MapStyleRecord("default", "Default", MapStyleRecord.Source.BUNDLE, assetPath = "default.json", fallbackUri = "fallback"),
        )
        every { remoteLocal.observeCachedStyles() } returns MutableStateFlow(
            listOf(MapStyleRecord("dark", "Dark", MapStyleRecord.Source.REMOTE, uri = "dark")),
        )
        every { selectionLocal.observeSelectedStyleId(any()) } returns MutableStateFlow("dark")
        every { resolver.resolve(any()) } returns MapResolvedStyle.Uri("dark")
        coEvery { remoteRemote.fetchStyles() } throws IllegalStateException("network")

        val repository = MapStyleRepositoryImpl(bundled, remoteLocal, remoteRemote, selectionLocal, resolver)

        // When
        val result = repository.refreshRemoteStyles()
        val styles = repository.observeAvailableStyles().first()

        // Then
        result.isFailure shouldBe true
        styles.map { it.id } shouldBe listOf("default", "dark")
    }
}

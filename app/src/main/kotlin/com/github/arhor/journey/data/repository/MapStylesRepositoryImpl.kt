package com.github.arhor.journey.data.repository

import android.content.Context
import com.github.arhor.journey.core.common.State
import com.github.arhor.journey.core.common.ThrowableStateError
import com.github.arhor.journey.core.common.asFailure
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.model.MapStyle.Type
import com.github.arhor.journey.domain.repository.MapStylesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import javax.inject.Singleton

@Serializable
data class MapStyleRecord(
    val id: String,
    val name: String,
    val data: String,
)

@Singleton
class MapStylesRepositoryImpl @Inject constructor(
    private val json: Json,
    @ApplicationContext private val context: Context,
) : MapStylesRepository {

    private val path = "map/styles/remote.json"
    private val mutex = Mutex()
    private var asset = MutableStateFlow<State<List<MapStyle>, ThrowableStateError>>(State.Loading)

    init {
        runBlocking {
            initialize()
        }
    }

    val data: StateFlow<State<List<MapStyle>, ThrowableStateError>>
        get() = asset.asStateFlow()

    suspend fun initialize() {
        mutex.withLock {
            if (asset.value is State.Content) {
                return
            }

            asset.value = State.Loading
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.assets.open(path).use {
                        json.decodeFromStream(
                            deserializer = ListSerializer(elementSerializer = MapStyleRecord.serializer()),
                            stream = it,
                        )
                    }
                }
            }
            result
                .map { items ->
                    items.map {
                        MapStyle(
                            id = it.id,
                            name = it.name,
                            type = Type.REMOTE,
                            value = it.data
                        )
                    }
                }
                .onSuccess { asset.value = State.Content(it) }
                .onFailure { asset.value = it.asFailure() }
        }
    }

    override fun findAll(): List<MapStyle> = DEFAULT_STYLES.values.toList()
    /* ------------------------------------------ Internal implementation ------------------------------------------- */

    private companion object {
        private val MAP_STYLE_VOYAGER = MapStyle.remote(
            id = "voyager-gl-style",
            name = "Voyager",
            value = "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json",
        )
        private val MAP_STYLE_POSITRON = MapStyle.remote(
            id = "positron-gl-style",
            name = "Positron",
            value = "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json",
        )
        private val MAP_STYLE_DARK_MATTER = MapStyle.remote(
            id = "dark-matter-gl-style",
            name = "Dark Matter",
            value = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json",
        )

        val DEFAULT_STYLES = listOf(
            MAP_STYLE_VOYAGER,
            MAP_STYLE_POSITRON,
            MAP_STYLE_DARK_MATTER,
        ).associateBy { it.id }
    }
}

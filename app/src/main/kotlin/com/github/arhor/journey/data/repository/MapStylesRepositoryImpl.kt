package com.github.arhor.journey.data.repository

import android.content.Context
import com.github.arhor.journey.core.common.State
import com.github.arhor.journey.di.AppCoroutineScope
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.repository.MapStylesError
import com.github.arhor.journey.domain.repository.MapStylesRepository
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.IOException
import javax.inject.Singleton

@Serializable
data class MapStyleRecord(
    val id: String,
    val name: String,
    val value: String,
)

@Singleton
class MapStylesRepositoryImpl @Inject constructor(
    private val json: Json,
    private val context: Context,
    @AppCoroutineScope private val appScope: CoroutineScope,
) : MapStylesRepository {

    private val path = "map/styles/remote.json"
    private val mutex = Mutex()
    private val mapStyles = MutableStateFlow<State<List<MapStyle>, MapStylesError>>(State.Loading)

    init {
        appScope.launch {
            loadMapStyles()
        }
    }

    override fun observeMapStyles(): StateFlow<State<List<MapStyle>, MapStylesError>> =
        mapStyles.asStateFlow()

    /* ------------------------------------------ Internal implementation ------------------------------------------- */

    private suspend fun loadMapStyles() {
        mutex.withLock {
            mapStyles.update { State.Loading }

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

            mapStyles.value = result.fold(
                onSuccess = { State.Content(it.toMapStylesValue()) },
                onFailure = { State.Failure(it.toMapStylesError()) }
            )

        }
    }

    private fun List<MapStyleRecord>.toMapStylesValue(): List<MapStyle> =
        map {
            MapStyle.remote(
                id = it.id,
                name = it.name,
                value = it.value
            )
        }

    private fun Throwable.toMapStylesError(): MapStylesError =
        when (this) {
            is SerializationException -> MapStylesError.DeserializationFailure(cause = this)
            is IOException -> MapStylesError.AssetReadFailure(cause = this)
            else -> MapStylesError.UnknownFailure(cause = this)
        }
}

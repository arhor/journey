package com.github.arhor.journey.data.mapstyle

import jakarta.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.URL
import javax.inject.Singleton

@Singleton
class RemoteMapStyleRemoteDataSource @Inject constructor(
    private val json: Json,
) {
    suspend fun fetchStyles(): List<MapStyleRecord> {
        val payload = URL(REMOTE_STYLES_URL).readText()
        val root = json.parseToJsonElement(payload).jsonObject
        val stylesElement = root["styles"]?.jsonArray ?: return emptyList()

        return stylesElement.mapNotNull { styleElement ->
            runCatching {
                json.decodeFromJsonElement<RemoteStyleResponse>(styleElement)
            }.getOrNull()
        }.filter { it.id.isNotBlank() && it.name.isNotBlank() && it.uri.isNotBlank() }
            .map { style ->
                MapStyleRecord(
                    id = style.id,
                    name = style.name,
                    source = MapStyleRecord.Source.REMOTE,
                    uri = style.uri,
                )
            }
    }

    @Serializable
    private data class RemoteStyleResponse(
        val id: String,
        val name: String,
        val uri: String,
    )

    companion object {
        private const val REMOTE_STYLES_URL = "https://tiles.openfreemap.org/styles.json"
    }
}

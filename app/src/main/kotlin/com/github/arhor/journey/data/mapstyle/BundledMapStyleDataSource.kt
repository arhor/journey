package com.github.arhor.journey.data.mapstyle

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import javax.inject.Singleton

@Singleton
class BundledMapStyleDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {

    fun getStyles(): List<MapStyleRecord> {
        val assetFiles = context.assets.list(BUNDLED_STYLES_ASSET_DIR).orEmpty()
            .filter { it.endsWith(JSON_EXTENSION) }

        return assetFiles.flatMap { fileName ->
            val assetPath = "$BUNDLED_STYLES_ASSET_DIR/$fileName"
            val fileContent = runCatching {
                context.assets.open(assetPath).bufferedReader().use { it.readText() }
            }.getOrNull().orEmpty()

            parseBundledDefinitions(fileName = fileName, assetPath = assetPath, fileContent = fileContent)
        }
    }

    private fun parseBundledDefinitions(
        fileName: String,
        assetPath: String,
        fileContent: String,
    ): List<MapStyleRecord> {
        val element = runCatching { json.parseToJsonElement(fileContent) }.getOrNull() ?: return emptyList()

        return when {
            element is JsonObject && element["sources"] != null && element["layers"] != null -> {
                listOf(
                    MapStyleRecord(
                        id = fileName.removeSuffix(JSON_EXTENSION),
                        name = fileName.removeSuffix(JSON_EXTENSION).toDisplayName(),
                        source = MapStyleRecord.Source.BUNDLE,
                        assetPath = assetPath,
                        fallbackUri = DEFAULT_STYLE_FALLBACK_URI,
                    ),
                )
            }

            element is JsonObject && element["styles"] != null -> {
                parseManifestArray(element["styles"]!!.jsonArray)
            }

            element is JsonObject && element["id"] != null && element["style"] != null -> {
                listOfNotNull(parseManifestObject(element))
            }

            else -> emptyList()
        }
    }

    private fun parseManifestArray(elements: List<JsonElement>): List<MapStyleRecord> =
        elements.mapNotNull { item ->
            runCatching { item.jsonObject }
                .getOrNull()
                ?.let(::parseManifestObject)
        }

    private fun parseManifestObject(item: JsonObject): MapStyleRecord? {
        val id = item["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val styleElement = item["style"] ?: return null

        return MapStyleRecord(
            id = id,
            name = item["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: id.toDisplayName(),
            source = MapStyleRecord.Source.BUNDLE,
            rawStyleJson = json.encodeToString(JsonElement.serializer(), styleElement),
            fallbackUri = item["fallbackUri"]?.jsonPrimitive?.contentOrNull ?: DEFAULT_STYLE_FALLBACK_URI,
        )
    }

    private fun String.toDisplayName(): String =
        replace('_', ' ')
            .replace('-', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                token.replaceFirstChar { char ->
                    if (char.isLowerCase()) {
                        char.titlecase(Locale.ROOT)
                    } else {
                        char.toString()
                    }
                }
            }

    companion object {
        const val DEFAULT_STYLE_FALLBACK_URI = "https://tiles.openfreemap.org/styles/liberty"
        private const val BUNDLED_STYLES_ASSET_DIR = "map/styles"
        private const val JSON_EXTENSION = ".json"
    }
}

package com.github.arhor.journey.data.mapstyle

data class MapStyleRecord(
    val id: String,
    val name: String,
    val source: Source,
    val assetPath: String? = null,
    val uri: String? = null,
    val fallbackUri: String? = null,
) {
    enum class Source {
        BUNDLED,
        REMOTE,
    }
}

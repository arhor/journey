package com.github.arhor.journey.domain.model

data class ImportedActivityMetadata(
    val externalRecordId: String,
    val originPackageName: String,
    val timeBoundsHash: String,
)

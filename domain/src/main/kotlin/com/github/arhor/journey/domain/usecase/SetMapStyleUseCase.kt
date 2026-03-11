package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.map.model.MapStyle
import com.github.arhor.journey.domain.repository.MapStylesRepository
import com.github.arhor.journey.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetMapStyleUseCase @Inject constructor(
    private val mapStylesRepository: MapStylesRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(styleId: String) {
        val styleIds = mapStylesRepository.observeAvailableStyles().first().map { it.id }.toSet()
        val validStyleId = if (styleId in styleIds) styleId else MapStyle.DEFAULT_ID

        settingsRepository.setSelectedMapStyleId(validStyleId)
    }
}

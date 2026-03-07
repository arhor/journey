package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetMapStyleUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(style: MapStyle) {
        settingsRepository.setMapStyle(style)
    }
}

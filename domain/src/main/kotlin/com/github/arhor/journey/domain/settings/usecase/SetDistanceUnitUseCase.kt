package com.github.arhor.journey.domain.settings.usecase

import com.github.arhor.journey.domain.settings.model.DistanceUnit
import com.github.arhor.journey.domain.settings.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetDistanceUnitUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(unit: DistanceUnit) {
        settingsRepository.setDistanceUnit(unit)
    }
}

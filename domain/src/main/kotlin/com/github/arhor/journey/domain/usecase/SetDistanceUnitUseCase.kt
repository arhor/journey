package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.DistanceUnit
import com.github.arhor.journey.domain.repository.SettingsRepository
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

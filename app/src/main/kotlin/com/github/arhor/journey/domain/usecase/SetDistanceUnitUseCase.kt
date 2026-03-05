package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.DistanceUnit
import com.github.arhor.journey.domain.repository.SettingsRepository
import javax.inject.Inject

class SetDistanceUnitUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(unit: DistanceUnit) {
        repository.setDistanceUnit(unit)
    }
}


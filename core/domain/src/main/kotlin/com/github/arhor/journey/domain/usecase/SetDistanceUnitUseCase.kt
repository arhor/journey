package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.DistanceUnit
import com.github.arhor.journey.domain.model.error.UseCaseError
import com.github.arhor.journey.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetDistanceUnitUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(unit: DistanceUnit): Output<Unit, UseCaseError> =
        runSuspendingUseCaseCatching("set distance unit") {
            settingsRepository.setDistanceUnit(unit)
        }
}

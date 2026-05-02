package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.repository.AppSettingsRepository
import javax.inject.Inject

class ObserveSelectedMapStyleUseCase @Inject constructor(
    private val repository: AppSettingsRepository,
) {
    operator fun invoke() = repository.observeSelectedMapStyle()
}

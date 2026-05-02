package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.repository.AppSettingsRepository
import javax.inject.Inject

class SetSelectedMapStyleUseCase @Inject constructor(
    private val repository: AppSettingsRepository,
) {
    suspend operator fun invoke(styleId: String) = repository.setSelectedMapStyle(styleId)
}

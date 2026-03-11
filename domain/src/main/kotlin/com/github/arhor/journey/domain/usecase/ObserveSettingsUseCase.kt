package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.settings.model.AppSettings
import com.github.arhor.journey.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(): Flow<AppSettings> = settingsRepository.observeSettings()
}

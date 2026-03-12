package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.usecase.internal.resolveMapStyleId
import com.github.arhor.journey.domain.model.AppSettings
import com.github.arhor.journey.domain.repository.MapStylesRepository
import com.github.arhor.journey.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val mapStylesRepository: MapStylesRepository,
) {
    operator fun invoke(): Flow<AppSettings> =
        settingsRepository
            .observeSettings()
            .map(::withResolvedMapStyleId)

    private suspend fun withResolvedMapStyleId(settings: AppSettings): AppSettings {
        val availableStyles = mapStylesRepository.findAll()
        val resolvedMapStyleId = availableStyles.resolveMapStyleId(settings.selectedMapStyleId)

        if (resolvedMapStyleId != null && resolvedMapStyleId != settings.selectedMapStyleId) {
            settingsRepository.setSelectedMapStyleId(resolvedMapStyleId)
        }

        return settings.copy(selectedMapStyleId = resolvedMapStyleId)
    }
}

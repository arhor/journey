package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.State
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.usecase.internal.resolveMapStyleId
import com.github.arhor.journey.domain.model.AppSettings
import com.github.arhor.journey.domain.repository.MapStylesError
import com.github.arhor.journey.domain.repository.MapStylesRepository
import com.github.arhor.journey.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val mapStylesRepository: MapStylesRepository,
) {
    operator fun invoke(): Flow<AppSettings> =
        combine(
            settingsRepository.observeSettings(),
            mapStylesRepository.observeMapStyles(),
            ::withResolvedMapStyleId,
        )

    private suspend fun withResolvedMapStyleId(
        settings: AppSettings,
        mapStylesState: State<List<MapStyle>, MapStylesError>,
    ): AppSettings =
        when (mapStylesState) {
            State.Loading -> settings
            is State.Failure -> settings
            is State.Content -> resolveSelectedMapStyleId(settings, mapStylesState.value)
        }

    private suspend fun resolveSelectedMapStyleId(
        settings: AppSettings,
        availableStyles: List<MapStyle>,
    ): AppSettings {
        val resolvedMapStyleId = availableStyles.resolveMapStyleId(settings.selectedMapStyleId)

        if (resolvedMapStyleId != null && resolvedMapStyleId != settings.selectedMapStyleId) {
            settingsRepository.setSelectedMapStyleId(resolvedMapStyleId)
        }

        return settings.copy(selectedMapStyleId = resolvedMapStyleId)
    }
}

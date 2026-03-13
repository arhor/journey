package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.DomainError
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.core.common.combine
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.repository.MapStylesRepository
import com.github.arhor.journey.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveSelectedMapStyleUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val mapStylesRepository: MapStylesRepository,
) {
    operator fun invoke(): Flow<Output<MapStyle?, DomainError>> = combine(
        flow1 = settingsRepository.observeSettings(),
        flow2 = mapStylesRepository.observeMapStyles(),
    ) { settings, mapStyles ->

        settings.selectedMapStyleId
            ?.let { mapStyles.find { style -> style.id == it } }
            ?: mapStyles.firstOrNull()
    }
}

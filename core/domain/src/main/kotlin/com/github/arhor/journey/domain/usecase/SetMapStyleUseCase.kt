package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.fold
import com.github.arhor.journey.domain.repository.MapStylesRepository
import com.github.arhor.journey.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetMapStyleUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val mapStylesRepository: MapStylesRepository,
) {
    suspend operator fun invoke(mapStyleId: String) {
        val mapStyle =
            mapStylesRepository.observeMapStyles()
                .value
                .fold(onSuccess = { it }, onFailure = { emptyList() })
                .find { it.id == mapStyleId }

        if (mapStyle != null) {
            settingsRepository.setSelectedMapStyleId(mapStyle.id)
        }
    }
}

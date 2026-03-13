package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.fold
import com.github.arhor.journey.domain.repository.MapStylesRepository
import com.github.arhor.journey.domain.repository.SettingsRepository
import com.github.arhor.journey.domain.usecase.internal.resolveMapStyleId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetMapStyleUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val mapStylesRepository: MapStylesRepository,
) {
    suspend operator fun invoke(mapStyleId: String) {
        val id =
            mapStylesRepository.observeMapStyles()
                .value
                .fold(onSuccess = { it }, onFailure = { emptyList() })
                .resolveMapStyleId(mapStyleId)

        if (id != null) {
            settingsRepository.setSelectedMapStyleId(id)
        }
    }
}

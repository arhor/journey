package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.usecase.internal.resolveMapStyleId
import com.github.arhor.journey.domain.repository.MapStylesRepository
import com.github.arhor.journey.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetMapStyleUseCase @Inject constructor(
    private val mapStylesRepository: MapStylesRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(mapStyleId: String) {
        val id = mapStylesRepository.findAll().resolveMapStyleId(mapStyleId)

        if (id != null) {
            settingsRepository.setSelectedMapStyleId(id)
        }
    }
}

package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.repository.MapStyleRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetMapStyleUseCase @Inject constructor(
    private val mapStyleRepository: MapStyleRepository,
) {
    suspend operator fun invoke(styleId: String) {
        mapStyleRepository.selectStyle(styleId)
    }
}

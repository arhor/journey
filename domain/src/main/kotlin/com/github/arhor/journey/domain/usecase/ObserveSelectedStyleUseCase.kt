package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.map.model.ResolvedMapStyle
import com.github.arhor.journey.domain.repository.MapStylesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveSelectedStyleUseCase @Inject constructor(
    private val mapStylesRepository: MapStylesRepository,
) {
    operator fun invoke(): Flow<ResolvedMapStyle> = mapStylesRepository.observeSelectedStyle()
}

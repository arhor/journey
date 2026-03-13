package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.repository.MapStylesError
import com.github.arhor.journey.domain.repository.MapStylesRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetAllMapStylesUseCase @Inject constructor(
    private val mapStylesRepository: MapStylesRepository,
) {
    operator fun invoke(): StateFlow<Output<List<MapStyle>, MapStylesError>> =
        mapStylesRepository.observeMapStyles()
}

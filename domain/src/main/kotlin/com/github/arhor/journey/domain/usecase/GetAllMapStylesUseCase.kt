package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.domain.repository.MapStylesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetAllMapStylesUseCase @Inject constructor(
    private val mapStylesRepository: MapStylesRepository,
) {
    operator fun invoke(): List<MapStyle> = mapStylesRepository.findAll()
}

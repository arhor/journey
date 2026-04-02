package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.internal.toWatchtower
import com.github.arhor.journey.domain.model.Watchtower
import com.github.arhor.journey.domain.repository.WatchtowerRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetWatchtowerUseCase @Inject constructor(
    private val repository: WatchtowerRepository,
) {
    suspend operator fun invoke(id: String): Watchtower? =
        repository.getById(id)?.toWatchtower()
}

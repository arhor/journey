package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.internal.WatchtowerBalance
import com.github.arhor.journey.domain.internal.expandedByMeters
import com.github.arhor.journey.domain.internal.revealTilesAround
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.WatchtowerRevealSnapshot
import com.github.arhor.journey.domain.model.error.UseCaseError
import com.github.arhor.journey.domain.repository.WatchtowerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveClaimedWatchtowerRevealTilesUseCase @Inject constructor(
    private val repository: WatchtowerRepository,
) {
    operator fun invoke(
        bounds: GeoBounds,
        canonicalZoom: Int,
    ): Flow<Output<WatchtowerRevealSnapshot, UseCaseError>> =
        repository.observeInBounds(bounds.expandedByMeters(WatchtowerBalance.MAX_REVEAL_RADIUS_METERS))
            .map { records ->
                val tiles = buildSet {
                    records.forEach { record ->
                        val state = record.state ?: return@forEach
                        if (state.claimedAt == null || state.level <= 0) {
                            return@forEach
                        }

                        addAll(
                            revealTilesAround(
                                point = record.definition.location,
                                radiusMeters = WatchtowerBalance.revealRadiusMetersForLevel(state.level),
                                zoom = canonicalZoom,
                            ),
                        )
                    }
                }

                WatchtowerRevealSnapshot(tiles = tiles)
            }
            .toUseCaseOutputFlow("observe claimed watchtower reveal tiles")
}

package com.github.arhor.journey.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.domain.model.Hero
import com.github.arhor.journey.domain.model.HeroResource
import com.github.arhor.journey.domain.usecase.ObserveHeroResourcesUseCase
import com.github.arhor.journey.domain.usecase.ObserveHeroUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import javax.inject.Inject
import kotlin.math.floor

@HiltViewModel
class MapHudViewModel @Inject constructor(
    observeHero: ObserveHeroUseCase,
    observeHeroResources: ObserveHeroResourcesUseCase,
) : ViewModel() {

    val uiState: StateFlow<MapHudUiState> =
        combine(observeHero(), observeHeroResources()) { hero, resources ->
            hero.toMapHudUiState(resources = resources)
        }
            .catch { emit(MapHudUiState.Unavailable) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = MapHudUiState.Loading,
            )
}

internal fun Hero.toMapHudUiState(resources: List<HeroResource>): MapHudUiState {
    val resourcesByType = resources.associate { resource ->
        resource.resourceTypeId to resource.amount
    }

    return MapHudUiState.Content(
        heroInitial = name.toHeroInitial(),
        levelLabel = "Lv ${progression.level}",
        resources = ResourceType.entries.map { resourceType ->
            val amount = resourcesByType[resourceType.typeId] ?: 0

            MapHudResourceUiModel(
                resourceType = resourceType,
                amount = amount,
                amountLabel = formatResourceAmount(amount),
            )
        },
    )
}

internal fun formatResourceAmount(amount: Int): String {
    if (amount < THOUSAND) {
        return amount.toString()
    }

    return when {
        amount >= MILLION -> formatAbbreviatedAmount(amount = amount, divisor = MILLION, suffix = "M")
        else -> formatAbbreviatedAmount(amount = amount, divisor = THOUSAND, suffix = "K")
    }
}

private fun formatAbbreviatedAmount(
    amount: Int,
    divisor: Int,
    suffix: String,
): String {
    val value = amount.toDouble() / divisor.toDouble()
    val truncatedValue = if (value < 10.0) {
        floor(value * 10.0) / 10.0
    } else {
        floor(value)
    }
    val decimals = if (truncatedValue < 10.0) 1 else 0
    val formatted = "%.${decimals}f".format(Locale.US, truncatedValue).trimEnd('0').trimEnd('.')

    return "$formatted$suffix"
}

private fun String.toHeroInitial(): String =
    trim()
        .firstOrNull()
        ?.uppercaseChar()
        ?.toString()
        ?: "?"

private const val THOUSAND = 1_000
private const val MILLION = 1_000_000

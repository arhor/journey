package com.github.arhor.journey.feature.hero

sealed interface HeroEffect {

    data class Error(val message: String) : HeroEffect

    data class Success(val message: String) : HeroEffect
}

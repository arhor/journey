package com.github.arhor.journey.feature.home

sealed interface HomeEffect {

    data class Error(val message: String) : HomeEffect

    data class Success(val message: String) : HomeEffect
}

package com.github.arhor.journey.ui.views.home

sealed interface HomeEffect {

    data class Error(val message: String) : HomeEffect
}

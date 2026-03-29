package com.github.arhor.journey.feature.map

import android.content.Context
import android.content.ContextWrapper

interface MiniGameHost {
    fun openMiniGame()
}

internal fun Context.findMiniGameHost(): MiniGameHost {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is MiniGameHost) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    error("PoiDetailsRoute must be hosted in a MiniGameHost")
}

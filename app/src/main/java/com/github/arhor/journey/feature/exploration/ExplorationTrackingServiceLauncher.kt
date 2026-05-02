package com.github.arhor.journey.feature.exploration

import android.content.Context
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExplorationTrackingServiceLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun start() {
        ContextCompat.startForegroundService(
            context,
            ExplorationTrackingForegroundService.startIntent(context),
        )
    }

    fun stop() {
        context.stopService(
            ExplorationTrackingForegroundService.startIntent(context),
        )
    }
}

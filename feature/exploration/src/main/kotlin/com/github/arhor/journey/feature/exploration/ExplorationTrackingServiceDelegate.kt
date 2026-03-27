package com.github.arhor.journey.feature.exploration

import javax.inject.Inject

class ExplorationTrackingServiceDelegate @Inject constructor(
    private val runtime: ExplorationTrackingRuntime,
) {
    fun handleCommand(
        action: String?,
        startForeground: () -> Unit,
        stopService: () -> Unit,
    ): Int {
        when (action) {
            null,
            ExplorationTrackingForegroundService.ACTION_START -> {
                startForeground()
                runtime.startIfNeeded()
            }

            ExplorationTrackingForegroundService.ACTION_STOP -> {
                runtime.stop()
                stopService()
            }
        }
        return android.app.Service.START_NOT_STICKY
    }
}

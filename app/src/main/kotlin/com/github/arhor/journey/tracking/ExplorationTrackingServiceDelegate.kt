package com.github.arhor.journey.tracking

import javax.inject.Inject

class ExplorationTrackingServiceDelegate @Inject constructor(
    private val runtime: ExplorationTrackingRuntime,
) {
    fun handleCommand(
        action: String?,
        startForeground: () -> Unit,
        stopService: () -> Unit,
    ): Int {
        return when (action) {
            null,
            ExplorationTrackingForegroundService.ACTION_START -> {
                startForeground()
                runtime.startIfNeeded()
                android.app.Service.START_NOT_STICKY
            }

            ExplorationTrackingForegroundService.ACTION_STOP -> {
                runtime.stop()
                stopService()
                android.app.Service.START_NOT_STICKY
            }

            else -> android.app.Service.START_NOT_STICKY
        }
    }
}

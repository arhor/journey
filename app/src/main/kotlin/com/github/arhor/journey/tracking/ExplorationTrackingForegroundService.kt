package com.github.arhor.journey.tracking

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ExplorationTrackingForegroundService : Service() {

    @Inject
    lateinit var delegate: ExplorationTrackingServiceDelegate

    @Inject
    lateinit var notificationFactory: ExplorationTrackingNotificationFactory

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return delegate.handleCommand(
            action = intent?.action,
            startForeground = {
                ServiceCompat.startForeground(
                    this,
                    ExplorationTrackingNotificationFactory.TRACKING_NOTIFICATION_ID,
                    notificationFactory.createOngoingNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            },
            stopService = {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            },
        )
    }

    companion object {
        const val ACTION_START = "com.github.arhor.journey.action.START_EXPLORATION_TRACKING"
        const val ACTION_STOP = "com.github.arhor.journey.action.STOP_EXPLORATION_TRACKING"

        fun startIntent(context: Context): Intent =
            Intent(context, ExplorationTrackingForegroundService::class.java).apply {
                action = ACTION_START
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, ExplorationTrackingForegroundService::class.java).apply {
                action = ACTION_STOP
            }
    }
}

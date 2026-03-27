package com.github.arhor.journey.feature.exploration

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExplorationTrackingNotificationFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun createOngoingNotification(): Notification {
        ensureNotificationChannel()

        val contentIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.let { launchIntent ->
                PendingIntent.getActivity(
                    context,
                    CONTENT_INTENT_REQUEST_CODE,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
        val stopIntent = PendingIntent.getService(
            context,
            STOP_ACTION_REQUEST_CODE,
            ExplorationTrackingForegroundService.stopIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, TRACKING_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(context.getString(R.string.tracking_notification_title))
            .setContentText(context.getString(R.string.tracking_notification_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .addAction(
                0,
                context.getString(R.string.tracking_notification_stop_action),
                stopIntent,
            )
            .build()
    }

    private fun ensureNotificationChannel() {
        val notificationManager = context.getSystemService<NotificationManager>() ?: return
        val channel = NotificationChannel(
            TRACKING_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.tracking_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.tracking_notification_channel_description)
        }

        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val TRACKING_NOTIFICATION_ID = 42
        const val TRACKING_NOTIFICATION_CHANNEL_ID = "exploration_tracking"

        private const val CONTENT_INTENT_REQUEST_CODE = 1
        private const val STOP_ACTION_REQUEST_CODE = 2
    }
}

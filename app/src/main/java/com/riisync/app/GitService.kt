/**
 * Foreground Service for background Git operations.
 * This service manages active Git tasks, ensuring they continue running even if the app's UI is not in focus,
 * and provides persistent progress notifications to the user.
 */
package com.riisync.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * A Service that runs in the foreground to perform background Git tasks like cloning and pulling.
 */
class GitService : Service() {

    /**
     * Helper methods for interacting with the GitService.
     */
    companion object {
        private const val CHANNEL_ID = "git_operations"
        private const val NOTIFICATION_ID = 101
        
        const val ACTION_START = "ACTION_START"
        const val ACTION_UPDATE = "ACTION_UPDATE"
        const val ACTION_STOP = "ACTION_STOP"
        
        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val EXTRA_PROGRESS = "EXTRA_PROGRESS"

        /**
         * Starts the service with an initial task title.
         */
        fun start(context: Context, title: String) {
            val intent = Intent(context, GitService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Updates the current foreground notification with new progress.
         */
        fun update(context: Context, title: String, progress: Float) {
            val intent = Intent(context, GitService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PROGRESS, progress)
            }
            context.startService(intent)
        }

        /**
         * Stops the foreground service.
         */
        fun stop(context: Context) {
            val intent = Intent(context, GitService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    /**
     * Handles incoming commands via Intents.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Git Operation"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, createNotification(title, 0f), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIFICATION_ID, createNotification(title, 0f))
                }
            }
            ACTION_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Git Operation"
                val progress = intent.getFloatExtra(EXTRA_PROGRESS, 0f)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, createNotification(title, progress))
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Creates or updates the notification displayed while the service is active.
     */
    private fun createNotification(title: String, progress: Float): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Git Operations",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        val progressInt = (progress * 100).toInt()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (progress >= 0) {
            builder.setProgress(100, progressInt, false)
            builder.setContentText("$progressInt%")
        } else {
            builder.setProgress(100, 0, true)
            builder.setContentText("Processing...")
        }

        return builder.build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

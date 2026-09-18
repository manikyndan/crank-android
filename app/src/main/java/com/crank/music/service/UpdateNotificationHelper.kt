package com.crank.music.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.crank.music.MainActivity
import com.crank.music.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateNotificationHelper @Inject constructor(
    // Explicit `@param:` target: without it Kotlin warns that the annotation site
    // will change from value-parameter to field in a future language version.
    @param:ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "crank_updates"
        const val CHANNEL_NAME = "App Updates"
        const val NOTIFICATION_ID_UPDATE_AVAILABLE = 1001
        const val NOTIFICATION_ID_DOWNLOAD_COMPLETE = 1002
        const val NOTIFICATION_ID_INSTALL_SUCCESS = 1003
    }

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for app updates"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun showUpdateAvailable(versionName: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "update_checker")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Update Available")
            .setContentText("Crank Music v$versionName is ready to install")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("A new version of Crank Music is available. Tap to download and install the latest features and improvements."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        notificationManager.notify(NOTIFICATION_ID_UPDATE_AVAILABLE, notification)
    }

    fun showDownloadComplete() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "update_checker")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Download Complete")
            .setContentText("Update is ready to install")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_DOWNLOAD_COMPLETE, notification)
    }

    fun showInstallSuccess() {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Update Installed")
            .setContentText("Crank Music has been updated successfully!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_INSTALL_SUCCESS, notification)
    }

    fun cancelAll() {
        notificationManager.cancel(NOTIFICATION_ID_UPDATE_AVAILABLE)
        notificationManager.cancel(NOTIFICATION_ID_DOWNLOAD_COMPLETE)
        notificationManager.cancel(NOTIFICATION_ID_INSTALL_SUCCESS)
    }
}

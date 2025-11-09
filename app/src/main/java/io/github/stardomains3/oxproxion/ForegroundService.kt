package io.github.stardomains3.oxproxion

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ForegroundService : Service() {

    private val FOREGROUND_CHANNEL_ID = "ForegroundChannel"
    private val CHANNEL_ID = "ForegroundServiceChannel"

    companion object {
        private var instance: ForegroundService? = null

        fun stopService() {
            instance?.stop()
        }

        fun updateNotificationStatus(title: String, contentText: String) {
            instance?.updateNotification(title, contentText)
        }

        @Volatile
        var isRunningForeground: Boolean = false
            private set
    }

    private fun stop() {
        try {
            stopSelf()
        } catch (e: Exception) {
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunningForeground = false
        instance = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannels()

        val notification = buildNotification("Connectivity Service", "Ensures reliable messaging connectivity", FOREGROUND_CHANNEL_ID)

        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }

        startForeground(1, notification, foregroundServiceType)
        isRunningForeground = true
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val foregroundChannel = NotificationChannel(
            FOREGROUND_CHANNEL_ID,
            "Connectivity Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notification for the running foreground service"
        }

        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Main Updates Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Channel for main notification updates"
        }

        notificationManager.createNotificationChannels(
            listOf(foregroundChannel, serviceChannel)
        )
    }

    fun updateNotification(title: String, contentText: String) {
        if (!isAppInForeground()) {
            updateNotificationWithChannel(title, contentText, CHANNEL_ID)
        }
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        for (appProcess in appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                appProcess.processName == packageName) {
                return true
            }
        }
        return false
    }

    private fun updateNotificationWithChannel(title: String, contentText: String, channelId: String) {
        val notification = buildNotification(title, contentText, channelId)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(2, notification)
    }

    private fun buildNotification(title: String, contentText: String, channelId: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent ?: Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcherrobot)
            .setContentIntent(pendingIntent)

        if (channelId == FOREGROUND_CHANNEL_ID) {
            builder.setOngoing(true)
        } else {
            builder.setOngoing(false).setAutoCancel(true)
        }

        return builder.build()
    }
}

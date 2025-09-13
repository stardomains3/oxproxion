package io.github.stardomains3.oxproxion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ForegroundService : Service() {

    private val CHANNEL_ID = "ForegroundServiceChannel"
    private val SILENT_CHANNEL_ID = "SilentUpdatesChannel" // New silent channel

    companion object {
        private var instance: ForegroundService? = null

        private const val STOP_ACTION = "io.github.stardomains3.oxproxion.STOP_SERVICE"

        fun stopService() {
            instance?.stop()
        }

        fun updateNotificationStatus(title: String, contentText: String) {
            instance?.updateNotification(title, contentText)
        }

        // New silent update function
        fun updateNotificationStatusSilently(title: String, contentText: String) {
            instance?.updateNotificationSilently(title, contentText)
        }

        @Volatile
        var isRunningForeground: Boolean = false
            private set
    }

    private fun stop() {
        try {
            stopSelf()
        } catch (e: Exception) {
         //   Log.e("ForegroundService", "Error stopping service", e)
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
        intent?.action?.let { action ->
            if (action == STOP_ACTION) {
                toggleNotiPreference()
                stopSelf()


                return START_NOT_STICKY
            }
        }

        /* if (!ChatServiceGate.shouldRunService) {
             stopSelf()
             return START_NOT_STICKY
         }*/

        createNotificationChannels() // Note: plural now
        val initialTitle = intent?.getStringExtra("initial_title") ?: "oxproxion is Running."  // Fallback if not provided
        val notification = buildNotification(initialTitle, "oxproxion is Ready.", SILENT_CHANNEL_ID)
       // val notification = buildNotification("oxproxion is Running.", "oxproxion is Ready.", SILENT_CHANNEL_ID)
        val foregroundServiceType =
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING

        startForeground(1, notification, foregroundServiceType)
       // startForeground(1, notification)
        isRunningForeground = true
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    // Updated to create both channels
    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        // Main foreground service channel
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Foreground Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Channel for foreground service"
        }

        // Silent updates channel
        val silentChannel = NotificationChannel(
            SILENT_CHANNEL_ID,
            "Silent Updates Channel",
            NotificationManager.IMPORTANCE_MIN // Silent
        ).apply {
            description = "Channel for silent notification updates"
            setSound(null, null)
            enableVibration(false)
        }

        notificationManager.createNotificationChannels(
            listOf(serviceChannel, silentChannel)
        )
    }

    // Regular update (uses main channel)
    fun updateNotification(title: String, contentText: String) {
        updateNotificationWithChannel(title, contentText, CHANNEL_ID)
    }

    // Silent update (uses silent channel)
    fun updateNotificationSilently(title: String, contentText: String) {
        updateNotificationWithChannel(title, contentText, SILENT_CHANNEL_ID)
    }

    // Helper function to update with specific channel
    private fun updateNotificationWithChannel(title: String, contentText: String, channelId: String) {
        val notification = buildNotification(title, contentText, channelId)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(1, notification)
    }

    // Helper function to build the notification with stop action
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

        val stopIntent = Intent(this, ForegroundService::class.java).apply {
            action = STOP_ACTION
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcherrobot)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(0, "Stop", stopPendingIntent) // 0 = no icon; replace with a drawable resource if available (e.g., android.R.drawable.ic_menu_close_clear_cancel)
            .build()
    }
    private fun toggleNotiPreference() {
        // Instantiate your SharedPreferencesHelper (adjust if it's not context-based)
        val prefsHelper = SharedPreferencesHelper(applicationContext)

        // Get current state
        val currentState =
            prefsHelper.getNotiPreference()  // Default to true if null, like your original toggleNoti()

        // Toggle
        val newNotiState = !currentState

        // Save
        prefsHelper.saveNotiPreference(newNotiState)

    }
}

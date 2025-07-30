package io.github.stardomains3.oxproxion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.apply
import kotlin.jvm.java

class ForegroundService : Service() {

    private val CHANNEL_ID = "ForegroundServiceChannel"

    companion object {
        private var instance: ForegroundService? = null

        fun stopService() {
           // Log.d("ForegroundService", "stopService() called")
            instance?.stop()
        }
    }

    private fun stop() {
       // Log.d("ForegroundService", "Stopping service")
        try {
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
            Log.e("ForegroundService", "Error stopping service", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
       // Log.d("ForegroundService", "Service created")
    }

    override fun onDestroy() {
        super.onDestroy()
       // Log.d("ForegroundService", "Service destroyed")
        instance = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        // Create intent to bring app to foreground
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

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("oxproxion is running")
            .setContentText("You are in an active chat.")
            .setSmallIcon(R.mipmap.ic_launcherrobot)
            .setContentIntent(pendingIntent) // This will bring app to foreground
            .setOngoing(true) // Makes it harder to swipe away
            .build()

        startForeground(1, notification)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Foreground Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }
}

package io.github.stardomains3.oxproxion

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import org.commonmark.parser.Parser
import org.commonmark.renderer.text.TextContentRenderer

class ForegroundService : Service(), TextToSpeech.OnInitListener {

    private val FOREGROUND_CHANNEL_ID = "ForegroundChannel"
    private val CHANNEL_ID = "ForegroundServiceChannel"

    private val TOGGLE_TTS_ACTION = "TOGGLE_TTS_CHANNEL_2"
    private val DISMISS_ACTION = "DISMISS_CHANNEL_2"

    private var tts: TextToSpeech? = null
    private var isTtsActive = false
    private var isTtsUpdate = false
    private var lastUpdateTitle: String? = null
    private var lastUpdateText: String? = null

    companion object {
        private var instance: ForegroundService? = null

        fun stopService() {
            instance?.stop()
        }

        fun updateNotificationStatus(title: String, contentText: String) {
            instance?.updateNotification(title, contentText)
        }

        fun dismissNotificationIfNotSpeaking() {
            instance?.dismissIfNotSpeaking()
        }

        fun stopTtsSpeaking() {
            instance?.stopTts(false)
        }

        @Volatile
        var isRunningForeground: Boolean = false
            private set
    }

    private fun stop() {
        try {
            stopSelf()
        } catch (e: Exception) {
            //Log.e("ForegroundService", "Error stopping service", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        initTTS()
    }

    private fun initTTS() {
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == "fg_tts") {
                        handleTtsFinished()
                    }
                }
                override fun onError(utteranceId: String?) {
                    if (utteranceId == "fg_tts") {
                        handleTtsFinished()
                    }
                }
            })
        }
    }

    private fun handleTtsFinished() {
        tts?.stop()
        isTtsActive = false
        if (isAppInForeground()) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.cancel(2)
        } else {
            // Refresh notification to show "Speak" button silently (background)
            lastUpdateTitle?.let { title ->
                lastUpdateText?.let { text ->
                    isTtsUpdate = true
                    updateNotificationWithChannel(title, text, CHANNEL_ID)
                    isTtsUpdate = false
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        isTtsActive = false
        isRunningForeground = false
        instance = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                TOGGLE_TTS_ACTION -> {
                    if (isTtsActive) {
                        stopTts(true)
                        if (isAppInForeground()) {
                            val notificationManager = getSystemService(NotificationManager::class.java)
                            notificationManager.cancel(2)
                        }
                    } else {
                        startTtsForChannel2()
                    }
                    return START_NOT_STICKY
                }
                DISMISS_ACTION -> {
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    notificationManager.cancel(2)
                    tts?.stop()
                    isTtsActive = false
                    return START_NOT_STICKY
                }
            }
        }

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
            NotificationManager.IMPORTANCE_HIGH
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

        notificationManager.createNotificationChannels(listOf(foregroundChannel, serviceChannel))
    }

    fun updateNotification(title: String, contentText: String) {
        if (!isAppInForeground()) {
            lastUpdateTitle = title
            lastUpdateText = contentText
            if (isTtsActive) {
                tts?.stop()
                isTtsActive = false
            }
            updateNotificationWithChannel(title, contentText, CHANNEL_ID)
        }
    }

    private fun dismissIfNotSpeaking() {
        if (!isTtsActive && isNotificationActive(2)) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.cancel(2)
        }
    }

    private fun stopTts(updateNotif: Boolean) {
        tts?.stop()
        isTtsActive = false
        if (updateNotif && lastUpdateTitle != null && lastUpdateText != null && isNotificationActive(2)) {
            isTtsUpdate = true
            updateNotificationWithChannel(lastUpdateTitle!!, lastUpdateText!!, CHANNEL_ID)
            isTtsUpdate = false
        }
    }

    private fun startTtsForChannel2() {
        val lastResponse = getLastAiResponseForChannel(2) ?: return
        val cleanText = stripMarkdownWithCommonMark(lastResponse)
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "fg_tts")
        isTtsActive = true
        if (lastUpdateTitle != null && lastUpdateText != null && isNotificationActive(2)) {
            isTtsUpdate = true
            updateNotificationWithChannel(lastUpdateTitle!!, lastUpdateText!!, CHANNEL_ID)
            isTtsUpdate = false
        }
    }
    private fun stripMarkdownWithCommonMark(text: String): String {
        try {
            val parser = Parser.builder().build()
            val document = parser.parse(text)
            val renderer = TextContentRenderer.builder().build()
            return renderer.render(document).trim()
        } catch (e: Exception) {
            // If parsing fails, remove basic markdown with regex as fallback
            return text.replace(Regex("\\*\\*|__|`|\\[|\\]"), "")
        }
    }
    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        for (appProcess in appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                appProcess.processName == packageName
            ) {
                return true
            }
        }
        return false
    }

    private fun isNotificationActive(notificationId: Int): Boolean {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val activeNotifications = notificationManager.activeNotifications
        return activeNotifications.any { it.id == notificationId }
    }

    private fun updateNotificationWithChannel(title: String, contentText: String, channelId: String) {
        lastUpdateTitle = title
        lastUpdateText = contentText
        val notification = buildNotification(title, contentText, channelId)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(2, notification)
    }

    private fun getLastAiResponseForChannel(channelId: Int): String? {
        val prefs: SharedPreferences = getSharedPreferences("MainAppPrefs", Context.MODE_PRIVATE)
        return prefs.getString("last_ai_response_channel_$channelId", null)
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

        val toggleIntent = Intent(this, ForegroundService::class.java).apply {
            action = TOGGLE_TTS_ACTION
        }
        val togglePendingIntent = PendingIntent.getService(
            this, 10, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, ForegroundService::class.java).apply {
            action = DISMISS_ACTION
        }
        val dismissPendingIntent = PendingIntent.getService(
            this, 11, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcherrobot)
            .setContentIntent(pendingIntent)

        if (channelId == FOREGROUND_CHANNEL_ID) {
            builder.setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
        } else {
            builder.setOngoing(false)
                .setDeleteIntent(dismissPendingIntent)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)

            if (!isTtsActive) {
                builder.addAction(android.R.drawable.ic_media_play, "Speak", togglePendingIntent)
            } else {
                builder.addAction(android.R.drawable.ic_media_pause, "Stop", togglePendingIntent)
            }
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPendingIntent)
            builder.addAction(android.R.drawable.ic_menu_info_details, "Open", pendingIntent)

            if (isTtsUpdate) {
                builder.setSilent(true)
                    .setOnlyAlertOnce(true)
            }
        }

        return builder.build()
    }
}
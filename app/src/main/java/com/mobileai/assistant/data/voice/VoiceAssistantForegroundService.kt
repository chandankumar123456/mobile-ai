package com.mobileai.assistant.data.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mobileai.assistant.MainActivity
import com.mobileai.assistant.R

/**
 * Foreground Service enabling continuous background voice conversation
 * within official Android platform and microphone permission boundaries.
 */
class VoiceAssistantForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                onServiceActionCallback?.invoke(ACTION_STOP)
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                onServiceActionCallback?.invoke(ACTION_PAUSE)
                updateNotification("Session Paused • Tap resume to continue", isPaused = true)
            }
            ACTION_RESUME -> {
                onServiceActionCallback?.invoke(ACTION_RESUME)
                updateNotification("Listening for your voice...", isPaused = false)
            }
            ACTION_START -> {
                val stateText = intent?.getStringExtra(EXTRA_STATE_TEXT) ?: "Listening for your voice..."
                startInForeground(stateText)
            }
        }

        return START_NOT_STICKY
    }

    private fun startInForeground(stateText: String) {
        val notification = buildNotification(stateText, isPaused = false)

        val hasMicPermission = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasMicPermission) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isRunning = true
    }

    private fun updateNotification(stateText: String, isPaused: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(stateText, isPaused))
    }

    private fun buildNotification(stateText: String, isPaused: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VoiceAssistantForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleActionIntent = Intent(this, VoiceAssistantForegroundService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val togglePendingIntent = PendingIntent.getService(
            this, 2, toggleActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic_notification)
            .setContentTitle("Mobile AI Voice Assistant")
            .setContentText(stateText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                0,
                if (isPaused) "Resume" else "Pause",
                togglePendingIntent
            )
            .addAction(
                0,
                "Stop Session",
                stopPendingIntent
            )
            .build()
    }

    private fun stopForegroundService() {
        isRunning = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Voice Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows persistent microphone status for background voice conversations"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "voice_assistant_foreground_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.mobileai.assistant.action.START_VOICE"
        const val ACTION_PAUSE = "com.mobileai.assistant.action.PAUSE_VOICE"
        const val ACTION_RESUME = "com.mobileai.assistant.action.RESUME_VOICE"
        const val ACTION_STOP = "com.mobileai.assistant.action.STOP_VOICE"

        const val EXTRA_STATE_TEXT = "extra_state_text"

        var isRunning: Boolean = false
            private set

        var onServiceActionCallback: ((String) -> Unit)? = null

        fun start(context: Context, stateText: String = "Listening for your voice...") {
            val intent = Intent(context, VoiceAssistantForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_STATE_TEXT, stateText)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, VoiceAssistantForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}

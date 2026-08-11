package com.katok.pro.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage
import com.katok.pro.MainActivity
import com.katok.pro.R
import com.katok.pro.util.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class KatokHmsMessageService : HmsMessageService() {

    companion object {
        private const val TAG = "KatokHMS"
    }

    override fun onNewToken(token: String?) {
        super.onNewToken(token)
        if (token != null) {
            Log.d(TAG, "🔥 New HMS token: $token")
            sendTokenToServer(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage?) {
        super.onMessageReceived(remoteMessage)
        if (remoteMessage == null) return
        Log.d(TAG, "📩 HMS message received")

        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            Log.d(TAG, "Data payload: $data")
            handleDataMessage(data)
        }

        val notification = remoteMessage.notification
        if (notification != null) {
            Log.d(TAG, "Notification payload: ${notification.title}")
            showHmsNotification(notification.title, notification.body)
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val type = data["type"]
        when (type) {
            "WAKE_UP" -> {
                Log.d(TAG, "🎯 WAKE_UP message received, starting WebSocket service")
                val accessToken = TokenManager.getInstance(this).getAccessToken()
                if (accessToken != null && accessToken.isNotEmpty()) {
                    WebSocketForegroundService.start(this)
                } else {
                    Log.e(TAG, "No access token, cannot start WebSocket service")
                }
            }
            "REAL", "ADMIN_MESSAGE" -> {
                val title = data["title"]
                val body = data["body"]
                showHmsNotification(title, body)
            }
        }
    }

    private fun showHmsNotification(title: String?, body: String?) {
        var notificationTitle = title ?: "Новое уведомление"
        var notificationBody = body ?: ""

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        createNotificationChannel()

        val builder = NotificationCompat.Builder(this, "notification_channel")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(notificationTitle)
            .setContentText(notificationBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationBody))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(System.currentTimeMillis().toInt(), builder.build())
        Log.d(TAG, "🔔 HMS notification shown")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "notification_channel",
                "Уведомления",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о событиях в приложении"
                setShowBadge(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun sendTokenToServer(token: String) {
        val accessToken = TokenManager.getInstance(this).getAccessToken()
        if (accessToken == null || accessToken.isEmpty()) {
            Log.d(TAG, "User not logged in, HMS token will be sent later")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            // Используем обновлённый метод, который регистрирует токен с указанием платформы
            val result = com.katok.pro.repository.UserRepository(this@KatokHmsMessageService)
                .registerPushToken(token, "HMS")
            when (result) {
                is com.katok.pro.model.NetworkResult.Success -> {
                    Log.d(TAG, "✅ HMS token sent to server")
                }
                is com.katok.pro.model.NetworkResult.Error -> {
                    Log.e(TAG, "Failed to send HMS token: ${result.message}")
                }
                else -> {}
            }
        }
    }
}
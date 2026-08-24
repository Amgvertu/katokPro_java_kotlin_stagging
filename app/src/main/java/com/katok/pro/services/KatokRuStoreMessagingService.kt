package com.katok.pro.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.katok.pro.MainActivity
import com.katok.pro.R
import com.katok.pro.model.NetworkResult
import com.katok.pro.repository.UserRepository
import com.katok.pro.util.SecurePreferences
import com.katok.pro.util.TokenManager
import com.katok.pro.util.WakeUpManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.rustore.sdk.pushclient.messaging.model.RemoteMessage  // ← правильный импорт
import ru.rustore.sdk.pushclient.messaging.service.RuStoreMessagingService

class KatokRuStoreMessagingService : RuStoreMessagingService() {

    companion object {
        private const val TAG = "KatokRuStore"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("PushDebug", "🔥 RuStore: onNewToken вызван, токен: ${token.take(20)}...")
        Log.d(TAG, "🔥 New RuStore token: $token")
        SecurePreferences.getInstance(this).saveRuStoreToken(token)
        sendTokenToServer(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("PushDebug", "📩 RuStore: onMessageReceived вызван")
        Log.d("PushDebug", "📩 Данные: ${message.data}")
        Log.d(TAG, "📩 RuStore message received")
        val dataMap = message.data
        Log.d(TAG, "Data: $dataMap")

        val type = dataMap["type"]
        Log.d(TAG, "Message type: $type")

        when (type) {
            "WAKE_UP" -> {
                Log.d(TAG, "⏳ WAKE_UP получен (RuStore)")
                WakeUpManager(this).handleWakeUp()
            }
            "REAL", "ADMIN_MESSAGE" -> {
                val title = dataMap["title"] ?: "Новое уведомление"
                val body = dataMap["body"] ?: ""
                showNotification(title, body)

                val intent = Intent("REFRESH_UNREAD_COUNT")
                sendBroadcast(intent)
            }
            else -> {
                Log.d(TAG, "Unknown message type: $type")
            }
        }
    }

    private fun showNotification(title: String, body: String) {
        createNotificationChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, "notification_channel")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(System.currentTimeMillis().toInt(), builder.build())
        Log.d(TAG, "🔔 RuStore notification shown")
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
            Log.d(TAG, "User not logged in, token will be sent later")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val result = UserRepository(this@KatokRuStoreMessagingService)
                .registerPushToken(token, "RUSTORE")
            when (result) {
                is NetworkResult.Success -> Log.d(TAG, "✅ RuStore token registered")
                is NetworkResult.Error -> Log.e(TAG, "Failed: ${result.message}")
                else -> {}
            }
        }
    }
}
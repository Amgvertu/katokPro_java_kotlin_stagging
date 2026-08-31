package com.katok.pro.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage
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

class KatokHmsMessageService : HmsMessageService() {

    companion object {
        private const val TAG = "KatokHMS"
        private val gson = Gson()
    }

    override fun onNewToken(token: String?) {
        super.onNewToken(token)
        if (token != null) {
            Log.d("PushDebug", "🔥 HMS: onNewToken вызван, токен: ${token.take(20)}...")
            Log.d(TAG, "🔥 New HMS token: $token")
            SecurePreferences.getInstance(this).saveHmsToken(token)
            sendTokenToServer(token)
        } else {
            Log.w("PushDebug", "⚠️ HMS: onNewToken получил null")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage?) {
        super.onMessageReceived(remoteMessage)
        if (remoteMessage == null) {
            Log.w("PushDebug", "⚠️ HMS: remoteMessage is null")
            return
        }
        Log.d("PushDebug", "📩 HMS: onMessageReceived вызван")
        Log.d("PushDebug", "📩 Данные: ${remoteMessage.data}")

        // Получаем данные как строку JSON и парсим в Map
        val dataString = remoteMessage.data
        Log.d(TAG, "Raw data: $dataString")
        val dataMap = parseDataToMap(dataString)

        if (dataMap != null && dataMap.isNotEmpty()) {
            handleDataMessage(dataMap)
        }

        val notification = remoteMessage.notification
        remoteMessage.notification?.let {
            Log.d("PushDebug", "🔔 Заголовок: ${it.title}, тело: ${it.body}")
        }
        if (notification != null) {
            Log.d(TAG, "Notification payload: ${notification.title}")
            showHmsNotification(notification.title, notification.body)
        }
    }

    private fun parseDataToMap(dataString: String?): Map<String, String>? {
        if (dataString.isNullOrEmpty()) return null
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson<Map<String, String>>(dataString, type)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse data string: $dataString", e)
            null
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val type = data["type"]
        Log.d(TAG, "Handling message type: $type")

        when (type) {
            "WAKE_UP" -> {
                Log.d(TAG, "⏳ WAKE_UP получен (HMS)")
                WakeUpManager(this).handleWakeUp()
            }
            "REAL", "ADMIN_MESSAGE", "NEW_NOTIFICATION" -> {
                val title = data["title"] ?: "Новое уведомление"
                val body = data["body"] ?: ""
                showHmsNotification(title, body)

                val intent = Intent("REFRESH_UNREAD_COUNT")
                intent.putExtra("type", "HMS")
                sendBroadcast(intent)
                Log.d(TAG, "Отправлен запрос на обновление счетчика")
            }
            else -> {
                Log.d(TAG, "Unknown message type: $type")
            }
        }
    }

    private fun showHmsNotification(title: String?, body: String?) {
        Log.d("PushDebug", "🔔 Показываем уведомление: title=$title, body=$body")
        val notificationTitle = title ?: "Новое уведомление"
        val notificationBody = body ?: ""

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
            val result = UserRepository(this@KatokHmsMessageService)
                .registerPushToken(token, "HMS")
            when (result) {
                is NetworkResult.Success -> {
                    Log.d(TAG, "✅ HMS token registered via /api/push/register")
                }
                is NetworkResult.Error -> {
                    Log.e(TAG, "Failed to register HMS token: ${result.message}")
                }
                else -> {}
            }
        }
    }

}
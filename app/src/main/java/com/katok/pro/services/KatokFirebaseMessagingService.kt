package com.katok.pro.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.katok.pro.MainActivity
import com.katok.pro.R
import com.katok.pro.model.NetworkResult
import com.katok.pro.repository.UserRepository
import com.katok.pro.util.SecurePreferences
import com.katok.pro.util.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class KatokFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "KatokFCM"
        private const val FCM_TOKEN_PREF = "fcm_token_pref"
        private const val FCM_TOKEN_KEY = "fcm_token"

        @JvmStatic
        fun getSavedFcmToken(context: Context): String? {
            return SecurePreferences.getInstance(context).getFcmToken()
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "🔥 New FCM token: $token")

        saveTokenLocally(token)
        sendTokenToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "📩 FCM message received")

        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            Log.d(TAG, "Data payload: $data")
            handleDataMessage(data)
        }

        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "Notification payload: ${notification.title}")
            handleNotificationMessage(notification)
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
            "NEW_NOTIFICATION" -> {
                val title = data["title"]
                val body = data["body"]
                val entityId = data["entityId"]
                val notificationType = data["notificationType"]
                showFcmNotification(title, body, notificationType, entityId)
            }
        }
    }

    private fun handleNotificationMessage(notification: RemoteMessage.Notification) {
        val title = notification.title
        val body = notification.body
        showFcmNotification(title, body, null, null)
    }

    private fun showFcmNotification(title: String?, body: String?, type: String?, entityId: String?) {
        var notificationTitle = title
        var notificationBody = body
        if (notificationTitle == null) notificationTitle = "Новое уведомление"
        if (notificationBody == null) notificationBody = ""

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (entityId != null) {
                putExtra("push_type", type)
                putExtra("entity_id", entityId)
            }
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
        Log.d(TAG, "🔔 FCM notification shown")
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

    private fun saveTokenLocally(token: String) {
        SecurePreferences.getInstance(this).saveFcmToken(token)
    }

    private fun sendTokenToServer(token: String) {
        val accessToken = TokenManager.getInstance(this).getAccessToken()
        if (accessToken == null || accessToken.isEmpty()) {
            Log.d(TAG, "User not logged in, token will be sent later")
            return
        }

        val userRepository = UserRepository(this)
        CoroutineScope(Dispatchers.IO).launch {
            val result = userRepository.updateFcmToken(token)
            when (result) {
                is NetworkResult.Success -> {
                    Log.d(TAG, "✅ FCM token sent to server")
                }
                is NetworkResult.Error -> {
                    Log.e(TAG, "Failed to send FCM token: ${result.message}")
                }
                else -> {}
            }
        }
    }
}
package com.katok.pro.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.katok.pro.MainActivity
import com.katok.pro.R
import com.katok.pro.model.Ad
import com.katok.pro.model.Notification
import com.katok.pro.model.RealtimeEvent
import com.katok.pro.model.Response
import com.katok.pro.model.admin.AdminMessage
import com.katok.pro.network.RealtimeEventBus

class NotificationsManager {

    companion object {
        private const val TAG = "NotificationsManager"
        private const val NOTIFICATION_CHANNEL_ID = "notification_channel"
        private val gson = Gson()
        private var lastNotificationId: Long = 0
        private var lastNotificationTime: Long = 0

        /**
         * Вызывается при получении нового уведомления через WebSocket.
         * Парсит JSON и показывает системное уведомление.
         *
         * @param context     Контекст (обычно из сервиса или активности)
         * @param jsonMessage JSON-строка уведомления
         */
        @JvmStatic
        fun onNotificationReceived(context: Context, jsonMessage: String) {
            Log.d(TAG, "📨 onNotificationReceived called with: $jsonMessage")
            try {
                val notification = gson.fromJson(jsonMessage, Notification::class.java)
                showPushNotification(context, notification)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing notification", e)
                showSimpleNotification(context, "Новое уведомление", jsonMessage)
            }
        }

        /**
         * Показывает информативное уведомление (например, при ошибке парсинга).
         */
        private fun showSimpleNotification(context: Context, title: String, content: String?) {
            createNotificationChannel(context)

            val intent = Intent(context, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Исправлено: content заменён на content ?: "" для безопасной передачи
            val shortContent = if (content != null && content.length > 50)
                content.substring(0, 47) + "..."
            else
                (content ?: "")

            val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(shortContent)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content ?: ""))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            manager?.notify(System.currentTimeMillis().toInt(), builder.build())
        }

        /**
         * Показывает основное push-уведомление.
         */
        private fun showPushNotification(context: Context, notification: Notification) {
            Log.d(TAG, "🔔 showPushNotification called: title=${notification.type}, content=${notification.content}, type=${notification.type}")
            if (notification.type.isNullOrEmpty() && notification.content.isNullOrEmpty()) {
                Log.d(TAG, "Empty notification, ignoring")
                return
            }
            val now = System.currentTimeMillis()
            if (notification.id == lastNotificationId && now - lastNotificationTime < 1000) {
                Log.d(TAG, "Duplicate notification ignored")
                return
            }
            lastNotificationId = notification.id
            lastNotificationTime = now

            var title = getNotificationTitle(notification.type)
            var content = notification.content

            if (title == null) title = "Новое уведомление"
            if (content == null) content = ""

            val shortContent = if (content.length > 50) content.substring(0, 47) + "..." else content

            createNotificationChannel(context)

            val intent = Intent(context, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Не добавляем extras, чтобы не открывать конкретное объявление

            val pendingIntent = PendingIntent.getActivity(
                context,
                notification.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(shortContent)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            if (manager != null) {
                manager.notify(notification.id.toInt(), builder.build())
                Log.d(TAG, "🔔 Notification shown: $title")
            }
        }

        fun showAdminPushNotification(context: Context, message: AdminMessage) {
            // Используем существующий канал "notification_channel"
            val title = message.title ?: "Сообщение от администратора"
            val content = message.content

            // Создаем Intent для открытия списка сообщений
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_messages", true)
            }
            val pendingIntent = PendingIntent.getActivity(
                context, message.id.toInt(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, "notification_channel")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            manager?.notify(message.id.toInt(), builder.build())
        }

        /**
         * Создаёт канал уведомлений (Android 8+).
         */
        private fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Уведомления",
                    NotificationManager.IMPORTANCE_HIGH
                )
                channel.description = "Уведомления о событиях в приложении"
                channel.setShowBadge(true)
                channel.enableVibration(true)
                channel.enableLights(true)

                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
                manager?.createNotificationChannel(channel)
                Log.d(TAG, "🔔 Notification channel created")
            }
        }

        /**
         * Возвращает заголовок уведомления в зависимости от типа.
         */
        private fun getNotificationTitle(type: String?): String? {
            if (type == null) return null
            return when (type) {
                "RESPONSE" -> "Новый отклик"
                "RESPONSE_ACCEPTED" -> "Отклик принят"
                "NEW_AD" -> "Новое объявление"
                else -> null
            }
        }
    }

    // В классе NotificationsManager
    private fun handleRealtimeMessage(jsonMessage: String) {
        try {
            val json = gson.fromJson(jsonMessage, JsonObject::class.java)
            val typeStr = json.get("type").asString
            val payloadElement = json.get("payload")

            val eventType = mapToEventType(typeStr)
            if (eventType != null && payloadElement != null) {
                val payload = parsePayload(eventType, payloadElement)
                val entityId = extractEntityId(eventType, payload)
                val event = RealtimeEvent(eventType, entityId, null, payload)
                RealtimeEventBus.getInstance().postEvent(event)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse realtime event", e)
        }
    }

    private fun mapToEventType(typeStr: String): RealtimeEvent.Type? {
        return when (typeStr) {
            "AD_CREATED" -> RealtimeEvent.Type.AD_CREATED
            "AD_UPDATED" -> RealtimeEvent.Type.AD_UPDATED
            "RESPONSE_ADDED" -> RealtimeEvent.Type.RESPONSE_ADDED
            "RESPONSE_REMOVED" -> RealtimeEvent.Type.RESPONSE_REMOVED
            "RESPONSE_APPROVED" -> RealtimeEvent.Type.RESPONSE_APPROVED
            "RESPONSE_REJECTED" -> RealtimeEvent.Type.RESPONSE_REJECTED
            "APPROVAL_CANCELLED" -> RealtimeEvent.Type.APPROVAL_CANCELLED
            else -> null
        }
    }

    private fun parsePayload(type: RealtimeEvent.Type, payloadElement: JsonElement): Any? {
        return when (type) {
            RealtimeEvent.Type.AD_CREATED,
            RealtimeEvent.Type.AD_UPDATED ->
                gson.fromJson(payloadElement, Ad::class.java)
            RealtimeEvent.Type.RESPONSE_ADDED,
            RealtimeEvent.Type.RESPONSE_REMOVED,
            RealtimeEvent.Type.RESPONSE_APPROVED,
            RealtimeEvent.Type.RESPONSE_REJECTED,
            RealtimeEvent.Type.APPROVAL_CANCELLED ->
                gson.fromJson(payloadElement, Response::class.java)
            else -> null
        }
    }

    private fun extractEntityId(type: RealtimeEvent.Type, payload: Any?): String? {
        return when (payload) {
            is Ad -> payload.id.toString()
            is Response -> payload.id
            else -> null
        }
    }
}

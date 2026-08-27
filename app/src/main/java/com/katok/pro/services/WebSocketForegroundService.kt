package com.katok.pro.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.katok.pro.MainActivity
import com.katok.pro.R
import com.katok.pro.network.ApiClient
import com.katok.pro.network.WebSocketManager
import com.katok.pro.network.WebSocketSubscriptionManager
import com.katok.pro.util.NotificationsManager
import com.katok.pro.util.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WebSocketForegroundService : Service(), ApiClient.TokenRefreshListener {

    companion object {
        private const val TAG = "WebSocketForeground"
        private const val CHANNEL_ID = "websocket_channel"
        private const val NOTIFICATION_ID = 1001
        private const val RECONNECT_DELAY = 5000

        @Volatile
        private var instance: WebSocketForegroundService? = null

        @Volatile
        private var sharedWebSocketManager: WebSocketManager? = null

        private val webSocketLock = Any()

        @Volatile
        private var isConnecting = false

        @Volatile
        private var isConnected = false

        @JvmStatic
        fun start(context: Context) {
            val intent = Intent(context, WebSocketForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        @JvmStatic
        fun stop(context: Context) {
            val intent = Intent(context, WebSocketForegroundService::class.java)
            context.stopService(intent)
        }

        @JvmStatic
        fun getInstance(): WebSocketForegroundService? = instance
    }

    private var currentToken: String? = null
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null   // ← перенесено из companion
    private val pendingAdIds = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        instance = this
        ApiClient.addTokenRefreshListener(this)
        createNotificationChannel()
        registerNetworkCallback()   // ← ВЫЗОВ функции
        try {
            startForeground(NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground", e)
            stopSelf()
        }
        // Не вызываем connectWebSocket() здесь – ждём токен в onStartCommand
    }

    private fun registerNetworkCallback() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network available, reconnecting WebSocket")
                    synchronized(webSocketLock) {
                        if (!isConnected && !isConnecting && currentToken != null) {
                            connectWebSocket()
                        }
                    }
                }
            }
            connectivityManager.registerDefaultNetworkCallback(networkCallback!!)
            Log.d(TAG, "Network callback registered")
        } else {
            // Для старых версий можно использовать BroadcastReceiver с ACTION_CONNECTIVITY_CHANGE
            Log.d(TAG, "Network callback not supported on this Android version")
        }
    }

    private fun connectWebSocket() {
        synchronized(webSocketLock) {
            // Отменяем любые запланированные переподключения
            if (reconnectRunnable != null) {
                reconnectHandler.removeCallbacks(reconnectRunnable!!)
                reconnectRunnable = null
            }

            if (currentToken == null || currentToken!!.isEmpty()) {
                Log.e(TAG, "No token, cannot connect WebSocket")
                return
            }

            // Если уже подключаемся или подключены – выходим
            if (isConnecting || isConnected) {
                Log.d(TAG, "Already connecting or connected, skip")
                return
            }

            isConnecting = true
            Log.d(TAG, "Starting WebSocket connection in background...")

            // ЗАПУСКАЕМ ПОДКЛЮЧЕНИЕ В ФОНОВОМ ПОТОКЕ
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Полностью останавливаем старый менеджер
                    withContext(Dispatchers.Main) {
                        sharedWebSocketManager?.let {
                            it.disconnect()
                            sharedWebSocketManager = null
                        }
                    }

                    val wsUrl = ApiClient.getCurrentWebSocketUrl()
                    Log.d(TAG, "Creating new WebSocketManager for URL: $wsUrl")

                    // Создаём WebSocketManager (это может занять время, поэтому в фоне)
                    val manager = WebSocketManager(currentToken!!, object : WebSocketManager.Listener {
                        override fun onConnected() {
                            synchronized(webSocketLock) {
                                isConnecting = false
                                isConnected = true
                            }
                            Log.d(TAG, "✅ WebSocket connected")

                            // Восстанавливаем отложенные подписки
                            if (pendingAdIds.isNotEmpty()) {
                                Log.d(TAG, "Restoring ${pendingAdIds.size} deferred subscriptions")
                                pendingAdIds.forEach { adId ->
                                    sharedWebSocketManager?.subscribeToAd(adId)
                                }
                                pendingAdIds.clear()
                            }
                            sharedWebSocketManager?.subscribeToAds()
                            sharedWebSocketManager?.subscribeToNotifications()
                        }

                        override fun onDisconnected() {
                            synchronized(webSocketLock) {
                                isConnecting = false
                                isConnected = false
                            }
                            Log.d(TAG, "WebSocket disconnected, scheduling reconnect")
                            scheduleReconnect()
                        }

                        override fun onNotificationReceived(jsonMessage: String) {
                            Log.d(TAG, "onNotificationReceived in service: $jsonMessage")
                            showSystemNotification(jsonMessage)
                        }

                        override fun onError(error: Throwable) {
                            synchronized(webSocketLock) {
                                isConnecting = false
                                isConnected = false
                            }
                            Log.e(TAG, "WebSocket error", error)
                            scheduleReconnect()
                        }
                    })

                    // Сохраняем менеджер и подключаемся
                    withContext(Dispatchers.Main) {
                        sharedWebSocketManager = manager
                        WebSocketSubscriptionManager.setWebSocketManager(manager)
                        manager.connect()
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error during WebSocket creation", e)
                    synchronized(webSocketLock) {
                        isConnecting = false
                        isConnected = false
                    }
                    scheduleReconnect()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WebSocket соединение",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Поддержание соединения для уведомлений"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Katok.pro")
            .setContentText("Приложение активно")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val token = TokenManager.getInstance(this).getAccessToken()
        if (token == null || token.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            "SUBSCRIBE_TO_ADS" -> {
                val adIds = intent.getStringArrayListExtra("ad_ids") ?: emptyList()
                synchronized(webSocketLock) {
                    if (isConnected && sharedWebSocketManager != null) {
                        adIds.forEach { adId ->
                            sharedWebSocketManager?.subscribeToAd(adId)
                        }
                        Log.d(TAG, "Subscribed to ${adIds.size} ads")
                    } else {
                        pendingAdIds.addAll(adIds)
                        Log.d(TAG, "Deferred subscription for ${adIds.size} ads")
                    }
                }
            }
        }

        // Запускаем подключение (если ещё не подключено) в фоне
        synchronized(webSocketLock) {
            if (sharedWebSocketManager == null || !isConnected) {
                currentToken = token
                // Вызов connectWebSocket() теперь сам запускает корутину
                connectWebSocket()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        // Отменяем запланированный reconnect
        reconnectRunnable?.let {
            reconnectHandler.removeCallbacks(it)
            reconnectRunnable = null
        }
        // Отписываемся от network callback
        networkCallback?.let {
            try {
                val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(it)
                Log.d(TAG, "Network callback unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister network callback", e)
            }
        }
        // Закрываем WebSocket
        synchronized(webSocketLock) {
            sharedWebSocketManager?.let {
                it.disconnect()
                sharedWebSocketManager = null
            }
            isConnected = false
            isConnecting = false
        }
        // Отписываемся от токена
        ApiClient.removeTokenRefreshListener(this)
        instance = null
        super.onDestroy()
        WebSocketSubscriptionManager.clearAllSubscriptions()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleReconnect() {
        synchronized(webSocketLock) {
            reconnectRunnable?.let {
                reconnectHandler.removeCallbacks(it)
            }
            reconnectRunnable = Runnable {
                Log.d(TAG, "Attempting to reconnect WebSocket")
                connectWebSocket()
            }
            reconnectHandler.postDelayed(reconnectRunnable!!, RECONNECT_DELAY.toLong())
        }
    }

    private fun showSystemNotification(jsonMessage: String) {
        Log.d(TAG, "showSystemNotification called with: $jsonMessage")
        NotificationsManager.onNotificationReceived(this, jsonMessage)
    }

    override fun onTokenRefreshed(newAccessToken: String?) {
        Log.d(TAG, "Token refreshed, reconnecting WebSocket")
        synchronized(webSocketLock) {
            currentToken = newAccessToken

            // Отменяем запланированное переподключение
            reconnectRunnable?.let {
                reconnectHandler.removeCallbacks(it)
                reconnectRunnable = null
            }

            // Полностью уничтожаем старый менеджер
            sharedWebSocketManager?.let {
                it.disconnect()
                sharedWebSocketManager = null
            }

            // Сбрасываем флаги
            isConnecting = false
            isConnected = false

            // Подключаемся с новым токеном
            connectWebSocket()
        }
    }

    fun getWebSocketManager(): WebSocketManager? {
        synchronized(webSocketLock) {
            return sharedWebSocketManager
        }
    }
}
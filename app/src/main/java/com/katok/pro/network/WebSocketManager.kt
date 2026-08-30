package com.katok.pro.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.katok.pro.model.Ad
import com.katok.pro.model.RealtimeEvent
import com.katok.pro.model.Response
import com.katok.pro.model.admin.AdminMessage
import com.katok.pro.repository.AdminMessageRepository
import com.katok.pro.util.NotificationsManager
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ua.naiksoftware.stomp.Stomp
import ua.naiksoftware.stomp.StompClient
import ua.naiksoftware.stomp.dto.LifecycleEvent
import ua.naiksoftware.stomp.dto.StompHeader
import ua.naiksoftware.stomp.dto.StompMessage

class WebSocketManager(
    private var authToken: String,
    listener: Listener? = null
) {

    // Обычная ссылка на слушателя (сильная)
    private var listener: Listener? = listener

    private val pendingAdSubscriptions = mutableSetOf<String>()

    interface Listener {
        fun onConnected()
        fun onDisconnected()
        fun onNotificationReceived(jsonMessage: String)
        fun onError(error: Throwable)
    }

    companion object {
        private const val TAG = "WebSocketManager"
        private const val MAX_RECONNECT_ATTEMPTS = 10
    }

    private var stompClient: StompClient? = null
    private var lifecycleDisposable: Disposable? = null
    private var topicDisposable: Disposable? = null
    private var publicTopicDisposable: Disposable? = null
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null
    private var reconnectAttempts = 0
    private val adSubscriptions = mutableMapOf<String, Disposable>()
    private val subscribedAdIds = mutableSetOf<String>()
    private val gson = Gson()
    private var adsTopicDisposable: Disposable? = null
    @Volatile
    private var isConnecting = false
    @Volatile
    private var isConnected = false
    private var manualDisconnect = false
    private var subscribedToTopics = false

    private var compositeDisposable = CompositeDisposable()

    init {
        Log.d(TAG, "WebSocketManager created", Exception())
        initStompClient()
    }

    private fun initStompClient() {
        disposeAllSubscriptions()
        // Полностью очищаем предыдущее состояние
        stompClient?.let {
            it.disconnect()
            stompClient = null
        }

        if (topicDisposable != null && !topicDisposable!!.isDisposed) {
            topicDisposable!!.dispose()
            topicDisposable = null
        }
        if (publicTopicDisposable != null && !publicTopicDisposable!!.isDisposed) {
            publicTopicDisposable!!.dispose()
            publicTopicDisposable = null
        }
        if (adsTopicDisposable != null && !adsTopicDisposable!!.isDisposed) {
            adsTopicDisposable!!.dispose()
            adsTopicDisposable = null
        }
        for (d in adSubscriptions.values) {
            if (d != null && !d.isDisposed) d.dispose()
        }
        adSubscriptions.clear()
        // subscribedAdIds не очищаем, так как хотим восстановить подписки после переподключения

        manualDisconnect = false
        subscribedToTopics = false
        isConnecting = true

        val wsUrl = ApiClient.getCurrentWebSocketUrl()
        if (wsUrl == null) {
            Log.e(TAG, "WebSocket URL is null")
            return
        }

        stompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, wsUrl)

        val connectHeaders = mutableListOf<StompHeader>()
        connectHeaders.add(StompHeader("Authorization", "Bearer $authToken"))
        connectHeaders.add(StompHeader("heart-beat", "10000,10000"))

        // Подписываемся только на lifecycle – топики подпишем после OPENED
        lifecycleDisposable = stompClient!!.lifecycle()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ event -> onLifecycleEvent(event) }, { error -> onError(error) })

        stompClient!!.connect(connectHeaders)
        Log.d(TAG, "Connecting with WebSocket (headers hidden for security)")
    }

    private fun onLifecycleEvent(event: LifecycleEvent) {
        Log.d(TAG, "STOMP Lifecycle: ${event.type}, exception: ${event.exception}")
        when (event.type) {
            LifecycleEvent.Type.OPENED -> {
                synchronized(this) {
                    isConnecting = false
                    isConnected = true
                }
                reconnectAttempts = 0

                if (!subscribedToTopics) {
                    subscribeToAds()
                    subscribeToNotifications()
                    subscribedToTopics = true
                }

                // Восстанавливаем подписки для уже известных объявлений
                for (adId in subscribedAdIds) {
                    doSubscribeToAd(adId)
                }
                // Выполняем отложенные подписки
                if (pendingAdSubscriptions.isNotEmpty()) {
                    Log.d(TAG, "🔄 Processing ${pendingAdSubscriptions.size} deferred subscriptions")
                    pendingAdSubscriptions.forEach { adId ->
                        doSubscribeToAd(adId)
                    }
                    pendingAdSubscriptions.clear()
                }

                listener?.onConnected()
            }
            LifecycleEvent.Type.CLOSED -> {
                synchronized(this) {
                    isConnected = false
                }
                subscribedToTopics = false
                listener?.onDisconnected()
                scheduleReconnect()
            }
            LifecycleEvent.Type.ERROR -> {
                synchronized(this) {
                    isConnecting = false
                    isConnected = false
                }
                subscribedToTopics = false
                Log.e(TAG, "STOMP error", event.exception)
                listener?.onError(event.exception)
                scheduleReconnect()
            }
            else -> {
                // Handle any other lifecycle events
            }
        }
    }

    private fun onMessage(message: StompMessage) {
        Log.d(TAG, "📩 STOMP message payload: ${message.payload}")
        listener?.onNotificationReceived(message.payload)
    }

    private fun onError(throwable: Throwable) {
        Log.e(TAG, "WebSocket error", throwable)
        isConnected = false
        listener?.onError(throwable)
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        synchronized(this) {
            if (manualDisconnect) return
        }
        reconnectRunnable?.let { reconnectHandler.removeCallbacks(it) }
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.d(TAG, "Max reconnection attempts reached")
            return
        }
        // Добавить проверку сети
        if (!isNetworkAvailable()) {
            Log.d(TAG, "No network, waiting before reconnect")
            // Запланировать повторную проверку через 30 секунд
            reconnectRunnable = Runnable {
                scheduleReconnect()
            }
            reconnectHandler.postDelayed(reconnectRunnable!!, 30000)
            return
        }
        reconnectAttempts++
        val delay = minOf(5000L * reconnectAttempts, 60000L)
        reconnectRunnable = Runnable {
            Log.d(TAG, "Reconnecting... (attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)")
            disconnect()
            initStompClient()
        }
        reconnectHandler.postDelayed(reconnectRunnable!!, delay)
    }

    private fun isNetworkAvailable(): Boolean {
        val context = ApiClient.getAppContext() ?: return true
        return NetworkUtils.isNetworkAvailable(context)
    }

    fun connect() {
        if (stompClient == null) {
            initStompClient()
        }
        if (stompClient == null) return
        synchronized(this) {
            if (isConnected) {
                Log.d(TAG, "Already connected, ignoring connect()")
                return
            }
            if (isConnecting) {
                Log.d(TAG, "Already connecting, ignoring connect()")
                return
            }
            isConnecting = true
        }
        stompClient!!.connect()
    }

    fun disconnect() {
        manualDisconnect = true
        subscribedToTopics = false

        // Отменяем запланированный reconnect
        reconnectRunnable?.let {
            reconnectHandler.removeCallbacks(it)
            reconnectRunnable = null
        }
        reconnectAttempts = 0

        // Отписываемся от всех подписок через CompositeDisposable
        disposeAllSubscriptions()

        // Очищаем коллекции
        adSubscriptions.clear()
        subscribedAdIds.clear()

        stompClient?.let {
            it.disconnect()
            stompClient = null
        }
        synchronized(this) {
            isConnected = false
            isConnecting = false
        }
    }

    fun isConnected(): Boolean = synchronized(this) { isConnected }

    fun subscribeToAd(adId: String) {
        Log.d("WebSocketManager", "subscribeToAd called for adId=$adId, isConnected=$isConnected")
        subscribedAdIds.add(adId)
        if (isConnected && stompClient != null) {
            doSubscribeToAd(adId)
            Log.d(TAG, "✅ Subscribed to ad $adId immediately")
        } else {
            pendingAdSubscriptions.add(adId)
            Log.d(TAG, "⏳ Deferred subscription for ad $adId (connection not ready)")
        }
    }

    private fun doSubscribeToAd(adId: String) {
        if (stompClient == null || !isConnected) {
            Log.w(TAG, "Cannot subscribe to ad $adId: stompClient=$stompClient, isConnected=$isConnected")
            return
        }
        val statusTopic = "/topic/ad/$adId/status"
        val responsesTopic = "/topic/ad/$adId/responses"
        Log.d(TAG, "Subscribing to $statusTopic and $responsesTopic")
        val statusDisposable = stompClient!!.topic(statusTopic)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { message ->
                    Log.d(TAG, "📩 RAW MESSAGE from /topic/ads: ${message.payload}")
                    handleAdEvent(adId, message.payload) },
                { throwable -> Log.e(TAG, "Status topic error", throwable) }
            )
        compositeDisposable.add(statusDisposable)
        adSubscriptions["${adId}_status"] = statusDisposable

        val responsesDisposable = stompClient!!.topic(responsesTopic)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { message ->
                    Log.d(TAG, "📩 RAW MESSAGE from /topic/ads: ${message.payload}")
                    handleAdEvent(adId, message.payload) },
                { throwable -> Log.e(TAG, "Responses topic error", throwable) }
            )
        compositeDisposable.add(responsesDisposable)
        adSubscriptions["${adId}_responses"] = responsesDisposable
        Log.d(TAG, "Subscribing to $statusTopic and $responsesTopic")
        Log.d(TAG, "Subscribing to ad $adId, status topic: /topic/ad/$adId/status, responses: /topic/ad/$adId/responses")
    }

    fun unsubscribeFromAd(adId: String) {
        Log.d("WebSocketManager", "subscribeToAd called for adId=$adId, isConnected=$isConnected")
        subscribedAdIds.remove(adId)
        val d1 = adSubscriptions.remove("${adId}_status")
        val d2 = adSubscriptions.remove("${adId}_responses")
        d1?.dispose()
        d2?.dispose()
    }

    private fun handleAdEvent(adId: String?, json: String) {
        Log.d("DEBUG_RINKS", "WebSocket raw JSON: $json")
        try {
            val obj = gson.fromJson(json, JsonObject::class.java)
            val typeStr = obj.get("type")?.asString ?: return
            Log.d(TAG, "handleAdEvent: typeStr = $typeStr, listener = $listener")
            if (typeStr == "NEW_AD") {
                Log.d(TAG, "📩 NEW_AD detected, calling onNotificationReceived")
                listener?.onNotificationReceived(json)
                Log.d(TAG, "📩 onNotificationReceived called")
                return
            }
            val entityId = when {
                obj.has("entityId") && !obj.get("entityId").isJsonNull -> obj.get("entityId").asString
                obj.has("relatedEntityId") && !obj.get("relatedEntityId").isJsonNull -> obj.get("relatedEntityId").asString
                else -> adId
            }
            val eventType = mapEventType(typeStr)
            if (eventType != null) {
                val payloadElement = if (obj.has("payload") && !obj.get("payload").isJsonNull) {
                    obj.get("payload")
                } else {
                    null
                }
                // ВАЖНО: для AD_DELETED не пытаемся парсить payload
                val eventPayload = if (payloadElement != null && eventType != RealtimeEvent.Type.AD_DELETED) {
                    parsePayload(eventType, payloadElement)
                } else {
                    null
                }
                val finalEntityId = entityId ?: (eventPayload as? Ad)?.id?.toString() ?: adId ?: ""
                val event = RealtimeEvent(eventType, finalEntityId, null, eventPayload)
                RealtimeEventBus.getInstance().postEvent(event)
                Log.d(TAG, "Posted event: $eventType, entityId=$finalEntityId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ad event", e)
        }
    }

    private fun mapEventType(typeStr: String): RealtimeEvent.Type? {
        return when (typeStr) {
            "AD_CREATED" -> RealtimeEvent.Type.AD_CREATED
            "AD_UPDATED" -> RealtimeEvent.Type.AD_UPDATED
            "AD_DELETED" -> RealtimeEvent.Type.AD_DELETED
            "RESPONSE" -> RealtimeEvent.Type.RESPONSE_ADDED
            "RESPONSE_ADDED" -> RealtimeEvent.Type.RESPONSE_ADDED
            "RESPONSE_REMOVED" -> RealtimeEvent.Type.RESPONSE_REMOVED
            "RESPONSE_APPROVED" -> RealtimeEvent.Type.RESPONSE_APPROVED
            "RESPONSE_REJECTED" -> RealtimeEvent.Type.RESPONSE_REJECTED
            "APPROVAL_CANCELLED" -> RealtimeEvent.Type.APPROVAL_CANCELLED
            "RESPONSE_WITHDRAWN" -> RealtimeEvent.Type.RESPONSE_WITHDRAWN
            else -> null
        }
    }

    private fun parsePayload(type: RealtimeEvent.Type, payloadElement: JsonElement): Any? {
        return when (type) {
            RealtimeEvent.Type.AD_CREATED,
            RealtimeEvent.Type.AD_DELETED,
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

    fun subscribeToAds() {
        if (stompClient == null || !isConnected) return
        if (adsTopicDisposable != null && !adsTopicDisposable!!.isDisposed) {
            adsTopicDisposable!!.dispose()
        }
        adsTopicDisposable = stompClient!!.topic("/topic/ads")
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { message ->
                    Log.d(TAG, "📩 RAW MESSAGE from /topic/ads: ${message.payload}")
                    handleAdEvent(null, message.payload) },
                { throwable -> Log.e(TAG, "Ads topic error", throwable) }
            )
        compositeDisposable.add(adsTopicDisposable!!)
    }

    fun subscribeToNotifications() {
        if (stompClient == null || !isConnected) return

        // Отписываемся от старых подписок, если они есть
        if (topicDisposable != null && !topicDisposable!!.isDisposed) {
            topicDisposable!!.dispose()
            compositeDisposable.remove(topicDisposable!!)
        }
        if (publicTopicDisposable != null && !publicTopicDisposable!!.isDisposed) {
            publicTopicDisposable!!.dispose()
            compositeDisposable.remove(publicTopicDisposable!!)
        }

        // Подписка на личную очередь уведомлений
        topicDisposable = stompClient!!.topic("/user/queue/notifications")
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { message ->
                    Log.d(TAG, "📩 RAW MESSAGE from /topic/ads: ${message.payload}")
                    onMessage(message) },
                { throwable -> Log.e(TAG, "User queue error", throwable) }
            )
        compositeDisposable.add(topicDisposable!!)

        // Подписка на публичный топик уведомлений
        publicTopicDisposable = stompClient!!.topic("/topic/notifications")
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { message ->
                    Log.d(TAG, "📩 RAW MESSAGE from /topic/ads: ${message.payload}")
                    onMessage(message) },
                { throwable -> Log.e(TAG, "Public topic error", throwable) }
            )
        compositeDisposable.add(publicTopicDisposable!!)
        subscribeToAdminMessages()

        Log.d(TAG, "Subscribed to notification topics")
        Log.d(TAG, "Subscribing to /user/queue/notifications")
        Log.d(TAG, "Subscribing to /topic/notifications")
        Log.d(TAG, "Subscribing to /topic/ads")
    }

    fun subscribeToAdminMessages() {
        if (stompClient == null || !isConnected) {
            Log.d(TAG, "Cannot subscribe to admin messages: not connected")
            return
        }
        val adminTopicDisposable = stompClient!!.topic("/user/queue/admin-messages")
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { message ->
                    Log.d(TAG, "📩 Admin message received: ${message.payload}")
                    handleAdminMessage(message.payload)
                },
                { error -> Log.e(TAG, "Admin messages topic error", error) }
            )
        compositeDisposable.add(adminTopicDisposable)
    }

    private fun handleAdminMessage(json: String) {
        try {
            val adminMessage = gson.fromJson(json, AdminMessage::class.java)
            val context = ApiClient.getAppContext() ?: return
            val repository = AdminMessageRepository(context)
            // Сохраняем в фоновом потоке
            CoroutineScope(Dispatchers.IO).launch {
                repository.addMessage(adminMessage)
                // Если категория PUSH – показываем системное уведомление
                if (adminMessage.category == "PUSH") {
                    NotificationsManager.showAdminPushNotification(context, adminMessage)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse admin message", e)
        }
    }

    private fun disposeAllSubscriptions() {
        if (!compositeDisposable.isDisposed) {
            compositeDisposable.dispose()
        }
        compositeDisposable = CompositeDisposable()

        // Очищаем отдельные переменные (если они не в CompositeDisposable)
        lifecycleDisposable?.let {
            if (!it.isDisposed) it.dispose()
        }
        topicDisposable?.let {
            if (!it.isDisposed) it.dispose()
        }
        publicTopicDisposable?.let {
            if (!it.isDisposed) it.dispose()
        }
        adsTopicDisposable?.let {
            if (!it.isDisposed) it.dispose()
        }
        for (d in adSubscriptions.values) {
            if (d != null && !d.isDisposed) {
                d.dispose()
            }
        }
        adSubscriptions.clear()

        lifecycleDisposable = null
        topicDisposable = null
        publicTopicDisposable = null
        adsTopicDisposable = null
    }

    fun sendStatus(active: Boolean) {
        stompClient?.send("/app/user/status", """{"active": $active}""")
            ?.subscribe(
                { Log.d("WebSocketManager", "✅ Статус отправлен: $active") },
                { error -> Log.e("WebSocketManager", "❌ Ошибка отправки статуса", error) }
            )
    }


}

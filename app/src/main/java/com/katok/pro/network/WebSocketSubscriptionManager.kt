package com.katok.pro.network

import android.util.Log

object WebSocketSubscriptionManager {
    private const val TAG = "WSSubscriptionMgr"
    private var currentManager: WebSocketManager? = null
    private val activeSubscriptions = mutableSetOf<String>()

    fun setWebSocketManager(manager: WebSocketManager?) {
        currentManager = manager
        if (manager != null) {
            restoreSubscriptions()
        }
    }

    fun subscribeToAdIds(ids: Collection<String>) {
        val toAdd = ids - activeSubscriptions
        val toRemove = activeSubscriptions - ids
        toRemove.forEach { adId ->
            currentManager?.unsubscribeFromAd(adId)
            Log.d(TAG, "Unsubscribed from $adId")
        }
        activeSubscriptions.removeAll(toRemove)
        toAdd.forEach { adId ->
            currentManager?.subscribeToAd(adId)
            activeSubscriptions.add(adId)
            Log.d(TAG, "Subscribed to $adId")
        }
    }

    fun clearAllSubscriptions() {
        activeSubscriptions.forEach { currentManager?.unsubscribeFromAd(it) }
        activeSubscriptions.clear()
    }

    private fun restoreSubscriptions() {
        if (activeSubscriptions.isEmpty()) return
        Log.d(TAG, "Restoring ${activeSubscriptions.size} subscriptions")
        activeSubscriptions.forEach { currentManager?.subscribeToAd(it) }
    }
}
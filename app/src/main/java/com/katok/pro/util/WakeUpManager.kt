package com.katok.pro.util

import android.content.Context
import android.os.PowerManager
import android.util.Log

class WakeUpManager(private val context: Context) {

    companion object {
        private const val TAG = "WakeUpManager"
        private const val WAKE_LOCK_TAG = "Katok:WakeUp"
    }

    private val wakeLock: PowerManager.WakeLock? by lazy {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            WAKE_LOCK_TAG
        )
    }

    fun handleWakeUp() {
        Log.d(TAG, "⏰ Получен WAKE_UP, пробуждаем устройство")

        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire(30 * 1000L)
                Log.d(TAG, "🔒 Wakelock установлен на 30 секунд")
            }
        }

        try {
            com.katok.pro.services.WebSocketForegroundService.start(context)
            Log.d(TAG, "✅ WebSocket сервис запущен")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка запуска WebSocket сервиса", e)
        }

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "🔓 Wakelock снят")
                }
            }
        }, 5000)
    }
}
package com.katok.pro.util

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.katok.pro.model.NetworkResult
import com.katok.pro.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.rustore.sdk.pushclient.RuStorePushClient
import kotlin.coroutines.resume
import kotlinx.coroutines.delay

class TokenRegistrationService(private val context: Context) {

    companion object {
        private const val TAG = "TokenRegistration"
    }

    /**
     * Регистрирует все push-токены на сервере
     * Вызывать после успешного логина
     */
    suspend fun registerAllTokens() {
        Log.d(TAG, "🚀 Начинаем регистрацию всех push-токенов")

        // 1. FCM токен
        registerFcmToken()

        // 2. HMS токен (если устройство поддерживает)
        registerHmsToken()

        // 3. RuStore токен (если устройство поддерживает)
        registerRuStoreToken()
    }

    private suspend fun registerFcmToken() {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            if (token != null && token.isNotEmpty()) {
                Log.d(TAG, "📱 FCM токен получен: ${token.take(20)}...")
                sendTokenToServer(token, "FCM")
            } else {
                Log.w(TAG, "⚠️ FCM токен пустой")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения FCM токена", e)
        }
    }

    private suspend fun registerHmsToken() {
        try {
            // Проверяем, есть ли HMS на устройстве
            if (isHuaweiDevice()) {
                // HMS токен получается в KatokHmsMessageService.onNewToken
                // Здесь мы просто проверяем, что он уже зарегистрирован
                val token = SecurePreferences.getInstance(context).getHmsToken()
                if (token != null && token.isNotEmpty()) {
                    Log.d(TAG, "📱 HMS токен найден: ${token.take(20)}...")
                    sendTokenToServer(token, "HMS")
                } else {
                    Log.w(TAG, "⚠️ HMS токен не найден, будет получен позже")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка регистрации HMS токена", e)
        }
    }

    private suspend fun registerRuStoreToken() {
        try {
            if (isRuStoreAvailable()) {
                Log.d(TAG, "📱 RuStore доступен, проверяем сохранённый токен")
                val token = SecurePreferences.getInstance(context).getRuStoreToken()
                if (token != null && token.isNotEmpty()) {
                    Log.d(TAG, "📱 RuStore токен найден в хранилище: ${token.take(20)}...")
                    sendTokenToServer(token, "RUSTORE")
                } else {
                    Log.w(TAG, "⚠️ RuStore токен не найден в хранилище, он будет получен позже через onNewToken")
                }
            } else {
                Log.w(TAG, "⚠️ RuStore недоступен на устройстве")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка регистрации RuStore токена", e)
        }
    }

    private suspend fun sendTokenToServer(token: String, platform: String) {
        val userRepository = UserRepository(context)
        var attempts = 0
        val maxAttempts = 3
        var success = false
        while (attempts < maxAttempts && !success) {
            attempts++
            try {
                val result = userRepository.registerPushToken(token, platform)
                when (result) {
                    is NetworkResult.Success -> {
                        Log.d(TAG, "✅ $platform токен зарегистрирован на сервере (попытка $attempts)")
                        success = true
                    }
                    is NetworkResult.Error -> {
                        Log.e(TAG, "❌ Ошибка регистрации $platform токена (попытка $attempts): ${result.message}")
                        if (attempts < maxAttempts) {
                            // ждём 1 секунду перед повтором
                            delay(1000)
                        }
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Исключение при регистрации $platform токена (попытка $attempts): ${e.message}")
                if (attempts < maxAttempts) {
                    delay(1000)
                }
            }
        }
        if (!success) {
            Log.e(TAG, "❌ Не удалось зарегистрировать $platform токен после $maxAttempts попыток")
        }
    }

    private fun isHuaweiDevice(): Boolean {
        return try {
            Class.forName("com.huawei.hms.api.HuaweiApiClient")
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isRuStoreAvailable(): Boolean {
        return try {
            Class.forName("ru.rustore.sdk.pushclient.RuStorePushClient")
            true
        } catch (e: Exception) {
            false
        }
    }
}

// Extension function для ожидания FCM токена
suspend fun com.google.android.gms.tasks.Task<String>.await(): String? {
    return withContext(Dispatchers.IO) {
        try {
            com.google.android.gms.tasks.Tasks.await(this@await)
        } catch (e: Exception) {
            null
        }
    }
}
package com.katok.pro.util

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.katok.pro.model.NetworkResult
import com.katok.pro.network.NetworkUtils
import com.katok.pro.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ru.rustore.sdk.pushclient.RuStorePushClient

class TokenRegistrationService(private val context: Context) {

    companion object {
        private const val TAG = "TokenRegistration"
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2000L
    }

    /**
     * Регистрирует все push-токены на сервере
     * Вызывать после успешного логина
     */
    suspend fun registerAllTokens() {
        Log.d(TAG, "🚀 Начинаем регистрацию всех push-токенов")
        Log.d("PushDebug", "🚀 registerAllTokens() called")

        // Проверяем интернет
        if (!NetworkUtils.isNetworkAvailable(context)) {
            Log.w(TAG, "⚠️ Нет интернета, токены будут зарегистрированы позже")
            return
        }

        // 1. FCM токен
        registerFcmToken()

        // 2. HMS токен (если устройство поддерживает)
        registerHmsToken()

        // 3. RuStore токен (если устройство поддерживает)
        registerRuStoreToken()
    }

    private suspend fun registerFcmToken() {
        Log.d("PushDebug", "🔵 Начинаем регистрацию FCM-токена")
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
        Log.d("PushDebug", "🔵 Начинаем регистрацию HMS-токена")
        try {
            if (isHuaweiDevice()) {
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
        Log.d("PushDebug", "🔵 Начинаем регистрацию Rustore-токена")
        try {
            if (isRuStoreAvailable()) {
                val token = getRuStoreToken()
                if (token != null && token.isNotEmpty()) {
                    Log.d(TAG, "📱 RuStore токен получен: ${token.take(20)}...")
                    SecurePreferences.getInstance(context).saveRuStoreToken(token)
                    sendTokenToServer(token, "RUSTORE")
                } else {
                    Log.w(TAG, "⚠️ RuStore токен не получен")
                }
            } else {
                Log.w(TAG, "⚠️ RuStore недоступен на устройстве")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка регистрации RuStore токена", e)
        }
    }

    /**
     * Явный запрос токена у RuStore SDK
     * В версии 7.3.0 getToken() возвращает Task<String>
     */
    private suspend fun getRuStoreToken(): String? {
        return try {
            val task = RuStorePushClient.getToken()
            val token = task.await()
            if (token != null && token.isNotEmpty()) {
                Log.d(TAG, "✅ RuStore токен получен: ${token.take(20)}...")
                token
            } else {
                Log.w(TAG, "⚠️ RuStore токен пустой")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения RuStore токена", e)
            null
        }
    }

    private suspend fun sendTokenToServer(token: String, platform: String) {
        val userRepository = UserRepository(context)
        var attempts = 0
        var success = false
        Log.d("PushDebug", "📤 sendTokenToServer() called for platform $platform")

        while (attempts < MAX_ATTEMPTS && !success) {
            attempts++
            try {
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    Log.w(TAG, "⚠️ Нет интернета, попытка $attempts отложена")
                    delay(RETRY_DELAY_MS)
                    continue
                }

                val result = userRepository.registerPushToken(token, platform)
                when (result) {
                    is NetworkResult.Success -> {
                        Log.d(TAG, "✅ $platform токен зарегистрирован на сервере (попытка $attempts)")
                        success = true
                    }
                    is NetworkResult.Error -> {
                        Log.e(TAG, "❌ Ошибка регистрации $platform токена (попытка $attempts): ${result.message}")
                        if (attempts < MAX_ATTEMPTS) {
                            delay(RETRY_DELAY_MS)
                        }
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Исключение при регистрации $platform токена (попытка $attempts): ${e.message}")
                if (attempts < MAX_ATTEMPTS) {
                    delay(RETRY_DELAY_MS)
                }
            }
        }

        if (!success) {
            Log.e(TAG, "❌ Не удалось зарегистрировать $platform токен после $MAX_ATTEMPTS попыток")
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

// Универсальная extension для ожидания Task (работает для любых типов)
suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T? {
    return withContext(Dispatchers.IO) {
        try {
            com.google.android.gms.tasks.Tasks.await(this@await)
        } catch (e: Exception) {
            null
        }
    }
}
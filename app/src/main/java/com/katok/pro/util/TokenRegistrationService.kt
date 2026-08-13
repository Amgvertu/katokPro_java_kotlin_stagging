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
            // Проверяем, есть ли RuStore на устройстве
            if (isRuStoreAvailable()) {
                // Получаем RuStore токен асинхронно
                val token = getRuStoreToken()
                if (token != null && token.isNotEmpty()) {
                    Log.d(TAG, "📱 RuStore токен получен: ${token.take(20)}...")
                    sendTokenToServer(token, "RUSTORE")
                } else {
                    Log.w(TAG, "⚠️ RuStore токен пустой")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка регистрации RuStore токена", e)
        }
    }

    /**
     * Получение RuStore токена с использованием suspendCancellableCoroutine
     * Согласно документации RuStore: RuStorePushClient.getToken() возвращает Task<String>
     */
    private suspend fun getRuStoreToken(): String? {
        return suspendCancellableCoroutine { continuation ->
            try {
                // Получаем токен через Task
                RuStorePushClient.getToken()
                    .addOnSuccessListener { token ->
                        Log.d(TAG, "✅ RuStore токен получен: ${token?.take(20)}")
                        continuation.resume(token)
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "❌ Ошибка получения RuStore токена", exception)
                        continuation.resume(null)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Исключение при получении RuStore токена", e)
                continuation.resume(null)
            }
        }
    }

    private suspend fun sendTokenToServer(token: String, platform: String) {
        val userRepository = UserRepository(context)
        val result = userRepository.registerPushToken(token, platform)
        when (result) {
            is NetworkResult.Success -> {
                Log.d(TAG, "✅ $platform токен зарегистрирован на сервере")
            }
            is NetworkResult.Error -> {
                Log.e(TAG, "❌ Ошибка регистрации $platform токена: ${result.message}")
            }
            else -> {}
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
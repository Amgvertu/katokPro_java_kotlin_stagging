package com.katok.pro.workers

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.katok.pro.network.ApiClient
import com.katok.pro.util.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class TokenRefreshWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        private const val TAG = "TokenRefreshWorker"
    }

    override fun doWork(): Result {
        Log.d(TAG, "Starting background token refresh")

        val tokenManager = TokenManager.getInstance(applicationContext)
        val refreshToken = tokenManager.getRefreshToken()

        if (refreshToken == null) {
            Log.d(TAG, "No refresh token available")
            return Result.failure()
        }

        // Проверяем, прошло ли 6 дней с последнего обновления
        val lastRefreshTime = tokenManager.getLastTokenRefreshTime()
        val now = System.currentTimeMillis()
        val sixDaysInMillis = 6 * 24 * 60 * 60 * 1000L

        if (now - lastRefreshTime < sixDaysInMillis) {
            Log.d(TAG, "Token was refreshed recently, skipping")
            return Result.success()
        }

        // Вызываем синхронное обновление токена (блокирует поток, но Worker уже в фоне)
        val success = runBlocking(Dispatchers.IO) {
            ApiClient.refreshAccessTokenAsync() != null
        }

        return if (success) {
            Log.d(TAG, "Token refreshed successfully in background")
            TokenRefreshScheduler.scheduleNextRefresh(applicationContext)
            Result.success()
        } else {
            Log.e(TAG, "Failed to refresh token")
            Result.failure()
        }
    }
}

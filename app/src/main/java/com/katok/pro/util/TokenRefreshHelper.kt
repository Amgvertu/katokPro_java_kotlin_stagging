package com.katok.pro.util

import android.content.Context
import com.katok.pro.model.NetworkResult
import com.katok.pro.repository.AuthRepository

class TokenRefreshHelper(private val context: Context) {

    private val authRepository = AuthRepository()
    private val tokenManager = TokenManager.getInstance(context)
    private val sessionManager = SessionManager(context)

    suspend fun refreshTokenIfNeeded(): Boolean {
        val refreshToken = tokenManager.getRefreshToken() ?: return false
        val result = authRepository.refreshToken()
        if (result is NetworkResult.Success) {
            val loginData = result.data
            tokenManager.saveTokens(loginData.accessToken, loginData.refreshToken)
            return true
        }
        return false
    }

    suspend fun logoutAndClear() {
        sessionManager.logout()
        tokenManager.clear()
        ProfileCacheManager(context).clear()
    }
}
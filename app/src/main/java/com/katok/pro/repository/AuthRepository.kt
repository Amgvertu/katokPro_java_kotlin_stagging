package com.katok.pro.repository

import com.katok.pro.model.*
import com.katok.pro.network.ApiClient
import com.katok.pro.network.ApiService
import com.katok.pro.network.safeApiCall
import com.katok.pro.network.safeApiCallWithNullableData
import com.katok.pro.util.TokenManager

class AuthRepository {

    private val apiService: ApiService
        get() = ApiClient.getApiService()

    suspend fun refreshToken(): NetworkResult<LoginResponse>? {
        val refreshToken = TokenManager.getInstance().getRefreshToken() ?: return null
        val request = RefreshTokenRequest(refreshToken)
        return safeApiCall { apiService.refreshToken(request) }
    }

    suspend fun login(phone: String, password: String): NetworkResult<LoginResponse> {
        val request = LoginRequest(phone, password)
        return safeApiCall { apiService.login(request) }
    }

    suspend fun register(phone: String, password: String): NetworkResult<User> {
        val request = RegisterRequest(phone, password)
        return safeApiCall { apiService.register(request) }
    }

    suspend fun getCurrentUser(): NetworkResult<User> {
        return safeApiCall { apiService.getCurrentUser() }
    }

    suspend fun sendRegistrationCode(phone: String): NetworkResult<CodeResponse?> {
        val body = hashMapOf("phone" to phone)
        return safeApiCallWithNullableData { apiService.sendRegistrationCode(body) }
    }

    suspend fun registerWithVerification(
        phone: String,
        password: String,
        code: String,
        countryId: Int,
        regionId: Int,
        cityId: Int
    ): NetworkResult<LoginResponse> {
        val request = RegisterWithCodeRequest(phone, password, code, countryId, regionId, cityId)
        return safeApiCall { apiService.registerWithVerification(request) }
    }

    suspend fun sendPasswordResetCode(phone: String): NetworkResult<CodeResponse?> {
        val body = hashMapOf("phone" to phone)
        return safeApiCallWithNullableData { apiService.sendPasswordResetCode(body) }
    }

    suspend fun resetPassword(phone: String, code: String, newPassword: String): NetworkResult<Void> {
        val body = hashMapOf("phone" to phone, "code" to code, "newPassword" to newPassword)
        return safeApiCall { apiService.resetPassword(body) }
    }
}
package com.katok.pro.repository

import android.content.Context
import com.katok.pro.model.*
import com.katok.pro.network.ApiClient
import com.katok.pro.network.ApiService
import com.katok.pro.network.safeApiCall
import com.katok.pro.network.safeApiCallIgnoreNullData
import com.katok.pro.util.ProfileCacheManager
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class UserRepository(context: Context) {

    private val apiService: ApiService
        get() = ApiClient.getApiService()
    private val profileCacheManager = ProfileCacheManager(context)

    suspend fun getCurrentUser(): NetworkResult<User> {
        return safeApiCall { apiService.getCurrentUser() }
    }

    suspend fun getMyProfile(): NetworkResult<Profile> {
        return safeApiCall { apiService.getMyProfile() }
    }

    suspend fun getUserById(userId: String): NetworkResult<User> {
        return safeApiCall { apiService.getUserById(userId) }
    }

    suspend fun getPublicProfile(userId: String): NetworkResult<Profile> {
        return safeApiCall { apiService.getPublicProfile(userId) }
    }

    suspend fun updateProfile(profile: Profile): NetworkResult<Profile> {
        return safeApiCall { apiService.updateProfile(profile) }
    }

    suspend fun changePassword(oldPassword: String, newPassword: String): NetworkResult<Void> {
        val request = ChangePasswordRequest(oldPassword, newPassword)
        return safeApiCall { apiService.changePassword(request) }
    }

    suspend fun changePhone(newPhone: String): NetworkResult<Void> {
        return safeApiCall { apiService.changePhone(newPhone) }
    }

    suspend fun sendPhoneChangeCode(newPhone: String): NetworkResult<CodeResponse> {
        val body = hashMapOf("phone" to newPhone)
        return safeApiCall { apiService.sendPhoneChangeCode(body) }
    }

    suspend fun changePhoneWithVerification(newPhone: String, code: String): NetworkResult<Void> {
        val body = hashMapOf("newPhone" to newPhone, "code" to code)
        return safeApiCall { apiService.changePhoneWithVerification(body) }
    }

    suspend fun uploadAvatar(imageFile: File): NetworkResult<String> {
        val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", imageFile.name, requestFile)
        return safeApiCall { apiService.uploadAvatar(body) }
    }

    suspend fun deleteAvatar(): NetworkResult<Unit> {
        return safeApiCallIgnoreNullData { apiService.deleteAvatar() }
    }

    suspend fun updateFcmToken(token: String): NetworkResult<Void> {
        val body = hashMapOf("token" to token)
        return safeApiCall { apiService.updateFcmToken(body) }
    }

    suspend fun cacheProfile(profile: Profile) {
        profileCacheManager.saveProfile(profile)
    }

    suspend fun getCachedProfile(): Profile? {
        return profileCacheManager.getCachedProfile()
    }

    suspend fun sendFeedback(request: FeedbackRequest): NetworkResult<Unit> {
        return safeApiCallIgnoreNullData {
            apiService.sendFeedback(request)
        }
    }

    suspend fun getTermsOfService(): NetworkResult<Agreement> {
        return safeApiCall { apiService.getTermsOfService() }
    }

    suspend fun registerPushToken(token: String, platform: String): NetworkResult<Unit> {
        val request = PushTokenRequest(token, platform)
        return safeApiCallIgnoreNullData { apiService.registerPushToken(request) }
    }
}
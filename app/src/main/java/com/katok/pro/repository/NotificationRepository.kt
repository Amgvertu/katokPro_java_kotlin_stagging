package com.katok.pro.repository

import com.katok.pro.model.*
import com.katok.pro.network.ApiClient
import com.katok.pro.network.ApiService
import com.katok.pro.network.safeApiCall
import com.katok.pro.network.safeApiCallIgnoreNullData

class NotificationRepository {

    private val apiService: ApiService
        get() = ApiClient.getApiService()

    suspend fun getNotificationSettings(): NetworkResult<NotificationSettings> {
        return safeApiCall { apiService.getNotificationSettings() }
    }

    suspend fun updateNotificationSettings(settings: NotificationSettings): NetworkResult<NotificationSettings> {
        return safeApiCall { apiService.updateNotificationSettings(settings) }
    }

    suspend fun getSubscriptions(): NetworkResult<List<NotificationSettings.Subscription>> {
        return safeApiCall { apiService.getSubscriptions() }
    }

    suspend fun addSubscription(type: Int, subType: Int): NetworkResult<Unit> {
        val sub = NotificationSettings.Subscription(type, subType)
        return safeApiCallIgnoreNullData { apiService.addSubscription(sub) }
    }

    suspend fun removeSubscription(type: Int, subType: Int): NetworkResult<Unit> {
        return safeApiCallIgnoreNullData { apiService.removeSubscription(type, subType) }
    }

    suspend fun getNotifications(onlyUnread: Boolean?, page: Int, size: Int, sort: String): NetworkResult<NotificationListResponse> {
        return safeApiCall { apiService.getNotifications(onlyUnread, page, size, sort) }
    }

    suspend fun getUnreadCount(): NetworkResult<Long> {
        return safeApiCall { apiService.getUnreadCount() }
    }

    suspend fun markNotificationsAsRead(ids: List<Long>): NetworkResult<Void> {
        return safeApiCall { apiService.markNotificationsAsRead(ids) }
    }

    suspend fun testNotification(): NetworkResult<Void> {
        return safeApiCall { apiService.testNotification() }
    }

    suspend fun testPublicNotification(): NetworkResult<Void> {
        return safeApiCall { apiService.testPublicNotification() }
    }

    suspend fun notifyResponseCancelled(adId: String, userId: String): NetworkResult<Void> {
        val body = hashMapOf("adId" to adId, "userId" to userId)
        return safeApiCall { apiService.notifyResponseCancelled(body) }
    }

    suspend fun notifyApprovalCancelled(adId: String, userId: String): NetworkResult<Void> {
        val body = hashMapOf("adId" to adId, "userId" to userId)
        return safeApiCall { apiService.notifyApprovalCancelled(body) }
    }

    suspend fun sendTestPush(title: String, body: String): NetworkResult<Unit> {
        return safeApiCallIgnoreNullData { apiService.sendTestPush(title, body) }
    }
}
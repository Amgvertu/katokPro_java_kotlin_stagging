package com.katok.pro.network

import com.katok.pro.model.Ad
import com.katok.pro.model.AdListResponse
import com.katok.pro.model.AdStatsResponse
import com.katok.pro.model.Advertising
import com.katok.pro.model.AdvertisingListResponse
import com.katok.pro.model.Agreement
import com.katok.pro.model.ApiResponse
import com.katok.pro.model.City
import com.katok.pro.model.ChangePasswordRequest
import com.katok.pro.model.CodeResponse
import com.katok.pro.model.DuplicateAd
import com.katok.pro.model.FeedbackRequest
import com.katok.pro.model.LoginRequest
import com.katok.pro.model.LoginResponse
import com.katok.pro.model.MyResponsesListResponse
import com.katok.pro.model.NetworkResult
import com.katok.pro.model.NotificationListResponse
import com.katok.pro.model.NotificationSettings
import com.katok.pro.model.Profile
import com.katok.pro.model.RefreshTokenRequest
import com.katok.pro.model.RegisterRequest
import com.katok.pro.model.RegisterWithCodeRequest
import com.katok.pro.model.Response as ModelResponse
import com.katok.pro.model.Rink
import com.katok.pro.model.RoleChangeRequest
import com.katok.pro.model.StatusChangeRequest
import com.katok.pro.model.UploadImageResponse
import com.katok.pro.model.User
import com.katok.pro.model.UserListResponse
import com.katok.pro.model.UserStatsResponse
import com.katok.pro.model.admin.AdminMessage
import com.katok.pro.model.admin.AdminMessageListResponse
import com.katok.pro.model.admin.AdminMessageRequest
import com.katok.pro.model.AdvertisingStatistics
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.*
import retrofit2.Response
interface ApiService {

    // Auth endpoints
    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<ApiResponse<LoginResponse>>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<LoginResponse>>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<User>>

    @GET("user/me")
    suspend fun getCurrentUser(): Response<ApiResponse<User>>

    @GET("user/{id}")
    suspend fun getUserById(@Path("id") userId: String): Response<ApiResponse<User>>

    @PUT("user/me/password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<ApiResponse<Void>>

    @PUT("user/me/phone")
    suspend fun changePhone(@Body newPhone: String): Response<ApiResponse<Void>>

    @DELETE("admin/users/{id}")
    suspend fun deleteUser(@Path("id") userId: String): Response<ApiResponse<Void>>

    // Profile endpoints
    @GET("profile/me")
    suspend fun getMyProfile(): Response<ApiResponse<Profile>>

    @GET("profile/{userId}")
    suspend fun getPublicProfile(@Path("userId") userId: String): Response<ApiResponse<Profile>>

    @PUT("profile/me")
    suspend fun updateProfile(@Body profile: Profile): Response<ApiResponse<Profile>>

    // Ads endpoints
    @GET("ads")
    suspend fun getAds(
        @Query("cityId") cityId: Int?,
        @Query("type") type: Int?,
        @Query("subType") subType: Int?,
        @Query("level") level: List<String>?,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Response<ApiResponse<AdListResponse>>

    @GET("ads/all")
    suspend fun getAllActiveAds(
        @Query("type") type: Int?,
        @Query("subType") subType: Int?,
        @Query("level") level: List<String>?,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Response<ApiResponse<AdListResponse>>

    @GET("ads/me")
    suspend fun getMyAds(
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Response<ApiResponse<AdListResponse>>

    @GET("ads/filter")
    suspend fun getFilteredAds(
        @Query("cityId") cityId: Int?,
        @Query("type") type: Int?,
        @Query("subType") subType: Int?,
        @Query("role") role: String?,
        @Query("level") level: List<String>?,
        @Query("dateFrom") dateFrom: String?,
        @Query("dateTo") dateTo: String?,
        @Query("timeFrom") timeFrom: String?,
        @Query("timeTo") timeTo: String?,
        @Query("rinkIds") rinkIds: List<Int>?,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Response<ApiResponse<AdListResponse>>

    @GET("ads/{id}")
    suspend fun getAdById(@Path("id") id: String): Response<ApiResponse<Ad>>

    @POST("ads")
    suspend fun createAd(@Body ad: Ad): Response<ApiResponse<Ad>>

    @PUT("ads/{id}")
    suspend fun updateAd(@Path("id") id: String, @Body ad: Ad): Response<ApiResponse<Ad>>

    @DELETE("ads/{id}")
    suspend fun deleteAd(@Path("id") id: String): Response<ApiResponse<Void>>

    // Responses endpoints
    @POST("ads/{adId}/responses")
    suspend fun createResponse(
        @Path("adId") adId: String,
        @Body response: ModelResponse
    ): Response<ApiResponse<ModelResponse>>

    @PUT("responses/{responseId}")
    suspend fun updateResponseStatus(
        @Path("responseId") responseId: String,
        @Query("status") status: String
    ): Response<ApiResponse<ModelResponse>>

    @DELETE("responses/{responseId}")
    suspend fun deleteResponse(@Path("responseId") responseId: String): Response<ApiResponse<Void>>

    // Rinks endpoints
    @GET("rinks/city/{cityId}")
    suspend fun getRinksByCity(@Path("cityId") cityId: Int): Response<ApiResponse<List<Rink>>>

    // Admin endpoints (без изменений)
    @GET("admin/users")
    suspend fun getAdminUsers(
        @Query("role") role: List<String>? = null,
        @Query("status") status: List<String>? = null,
        @Query("cityId") cityId: List<Int>? = null,
        @Query("team") team: List<String>? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sort") sort: String? = null
    ): Response<ApiResponse<UserListResponse>>

    @GET("admin/ads")
    suspend fun getAdminAds(
        @Query("status") status: List<String>? = null,
        @Query("type") type: List<Int>? = null,
        @Query("subType") subType: List<Int>? = null,
        @Query("cityId") cityId: List<Int>? = null,
        @Query("rinkId") rinkId: List<Int>? = null,
        @Query("authorId") authorId: List<String>? = null,
        @Query("level") level: List<String>? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sort") sort: String? = null
    ): Response<ApiResponse<AdListResponse>>

    @GET("admin/rinks")
    suspend fun getAdminRinks(): Response<ApiResponse<List<Rink>>>

    @PUT("admin/users/{userId}/role")
    suspend fun changeUserRole(
        @Path("userId") userId: String,
        @Body request: RoleChangeRequest
    ): Response<ApiResponse<Void>>

    @PUT("admin/users/{userId}/status")
    suspend fun changeUserStatus(
        @Path("userId") userId: String,
        @Body request: StatusChangeRequest
    ): Response<ApiResponse<Void>>

    // Moderation endpoints
    @GET("moderation/ads")
    suspend fun getModerationAds(
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Response<ApiResponse<AdListResponse>>

    @PUT("moderation/ads/{adId}/approve")
    suspend fun approveAd(@Path("adId") adId: String): Response<ApiResponse<Void>>

    @PUT("moderation/ads/{adId}/reject")
    suspend fun rejectAd(@Path("adId") adId: String): Response<ApiResponse<Void>>

    // Locations endpoints (без изменений)
    @GET("locations/countries")
    suspend fun getCountries(): Response<ApiResponse<List<com.katok.pro.model.Country>>>

    @GET("locations/regions/country/{countryId}")
    suspend fun getRegions(@Path("countryId") countryId: Int): Response<ApiResponse<List<com.katok.pro.model.Region>>>

    @GET("locations/cities/region/{regionId}")
    suspend fun getCities(@Path("regionId") regionId: Int): Response<ApiResponse<List<City>>>

    @GET("locations/cities/country/{countryId}")
    suspend fun getAllCitiesByCountry(@Path("countryId") countryId: Int): Response<ApiResponse<List<City>>>

    @POST("auth/send-registration-code")
    suspend fun sendRegistrationCode(@Body request: Map<String, String>): Response<ApiResponse<CodeResponse>>

    @POST("auth/register-with-verification")
    suspend fun registerWithVerification(@Body request: RegisterWithCodeRequest): Response<ApiResponse<LoginResponse>>

    @POST("auth/send-password-reset-code")
    suspend fun sendPasswordResetCode(@Body request: Map<String, String>): Response<ApiResponse<CodeResponse>>

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body request: Map<String, String>): Response<ApiResponse<Void>>

    @POST("ads/check-duplicate")
    suspend fun checkDuplicate(@Body ad: Ad): Response<ApiResponse<List<DuplicateAd>>>

    // Смена телефона – отправка кода
    @POST("user/me/send-phone-change-code")
    suspend fun sendPhoneChangeCode(@Body request: Map<String, String>): Response<ApiResponse<CodeResponse>>

    @PUT("user/me/phone-with-verification")
    suspend fun changePhoneWithVerification(@Body request: Map<String, String>): Response<ApiResponse<Void>>

    @GET("responses/my")
    suspend fun getMyResponses(@Query("page") page: Int, @Query("size") size: Int): Response<ApiResponse<MyResponsesListResponse>>

    @Multipart
    @POST("profile/me/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): Response<ApiResponse<String>>

    @DELETE("profile/me/avatar")
    suspend fun deleteAvatar(): Response<ApiResponse<Void>>

    // Notification settings
    @GET("notifications/settings")
    suspend fun getNotificationSettings(): Response<ApiResponse<NotificationSettings>>

    @PATCH("notifications/settings")
    suspend fun updateNotificationSettings(@Body settings: NotificationSettings): Response<ApiResponse<NotificationSettings>>

    // Subscriptions
    @GET("notifications/subscriptions")
    suspend fun getSubscriptions(): Response<ApiResponse<List<NotificationSettings.Subscription>>>

    @POST("notifications/subscriptions")
    suspend fun addSubscription(@Body subscription: NotificationSettings.Subscription): Response<ApiResponse<Void>>

    @DELETE("notifications/subscriptions")
    suspend fun removeSubscription(@Query("type") type: Int, @Query("subType") subType: Int): Response<ApiResponse<Void>>

    // Notifications history
    @GET("notifications")
    suspend fun getNotifications(
        @Query("onlyUnread") onlyUnread: Boolean?,
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("sort") sort: String
    ): Response<ApiResponse<NotificationListResponse>>

    @GET("user/messages/unread-count")
    suspend fun getUnreadAdminCount(): Response<ApiResponse<Long>>

    @PATCH("notifications/mark-read")
    suspend fun markNotificationsAsRead(@Body notificationIds: List<Long>): Response<ApiResponse<Void>>

    @POST("user/me/fcm-token")
    suspend fun updateFcmToken(@Body body: Map<String, String>): Response<ApiResponse<Void>>

    @POST("notifications/test-private")
    suspend fun testNotification(): Response<ApiResponse<Void>>

    @POST("notifications/test-public")
    suspend fun testPublicNotification(): Response<ApiResponse<Void>>

    // Уведомление автору при отмене отклика пользователем
    @POST("notifications/response-cancelled")
    suspend fun notifyResponseCancelled(@Body body: Map<String, String>): Response<ApiResponse<Void>>

    // Уведомление пользователю при отмене подтверждения автором
    @POST("notifications/approval-cancelled")
    suspend fun notifyApprovalCancelled(@Body body: Map<String, String>): Response<ApiResponse<Void>>

    @Multipart
    @POST("ads/{adId}/photos")
    suspend fun uploadAdPhoto(
        @Path("adId") adId: String,
        @Part file: MultipartBody.Part
    ): Response<ApiResponse<String>>

    @POST("feedback")
    suspend fun sendFeedback(@Body request: FeedbackRequest): Response<ApiResponse<Void>>

    @GET("agreements/terms_of_service")
    suspend fun getTermsOfService(): Response<ApiResponse<Agreement>>

    // ============= Админские сообщения =============
    @POST("admin/messages")
    suspend fun sendAdminMessage(@Body request: AdminMessageRequest): Response<ApiResponse<AdminMessage>>

    @GET("admin/messages")
    suspend fun getAdminSentMessages(
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Response<ApiResponse<AdminMessageListResponse>>

    // ============= Сообщения для пользователя =============
    @GET("user/messages")
    suspend fun getMyMessages(
        @Query("page") page: Int,
        @Query("size") size: Int
    ): Response<ApiResponse<AdminMessageListResponse>>

    @PUT("user/messages/{id}/read")
    suspend fun markMessageAsRead(@Path("id") id: Long): Response<ApiResponse<Void>>

    @PUT("user/messages/read-all")
    suspend fun markAllMessagesAsRead(): Response<ApiResponse<Void>>

    @GET("user/messages/unread-count")
    suspend fun getUnreadCount(): Response<ApiResponse<Long>>

    // Загрузка изображения для сообщения (если бэкенд реализовал отдельный эндпоинт)
    @Multipart
    @POST("admin/messages/upload-image")
    suspend fun uploadMessageImage(@Part file: MultipartBody.Part): Response<ApiResponse<UploadImageResponse>>

    // Удаление одного сообщения
    @DELETE("user/messages/{id}")
    suspend fun deleteMessage(@Path("id") id: Long): Response<ApiResponse<Void>>

    // (Опционально) удаление всех сообщений
    @DELETE("user/messages")
    suspend fun deleteAllMessages(): Response<ApiResponse<Void>>



    // Поиск пользователей (админский эндпоинт)
    @GET("admin/users/search")
    suspend fun searchUsers(
        @Query("query") query: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 10
    ): Response<ApiResponse<UserListResponse>>

    // Получение списка команд
    @GET("admin/users/teams")
    suspend fun getTeams(): Response<ApiResponse<List<String>>>

    @GET("admin/users")
    suspend fun getAdminUsers(
        @Query("search") search: String? = null,
        @Query("role") role: String? = null,
        @Query("status") status: String? = null,
        @Query("cityId") cityId: Int? = null,
        @Query("team") team: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sort") sort: String? = null
    ): Response<ApiResponse<UserListResponse>>

    @GET("admin/users/{id}")
    suspend fun getAdminUserById(@Path("id") userId: String): Response<ApiResponse<User>>

    @PUT("admin/users/{id}/profile")
    suspend fun updateAdminUserProfile(
        @Path("id") userId: String,
        @Body profile: Profile
    ): Response<ApiResponse<User>>

    @PUT("admin/users/{id}/role")
    suspend fun changeAdminUserRole(
        @Path("id") userId: String,
        @Query("role") role: String
    ): Response<ApiResponse<User>>

    @PUT("admin/users/{id}/status")
    suspend fun changeAdminUserStatus(
        @Path("id") userId: String,
        @Query("status") status: String
    ): Response<ApiResponse<User>>

    @DELETE("admin/users/{id}")
    suspend fun deleteAdminUser(
        @Path("id") userId: String,
        @Query("hardDelete") hardDelete: Boolean = false
    ): Response<ApiResponse<Void>>

    // ========== Получение всех городов (для фильтров и форм) ==========
    @GET("locations/cities/all")
    suspend fun getAllCities(): Response<ApiResponse<List<City>>>

    // ========== Статистика (мониторинг) ==========

    @GET("admin/statistics/users")
    suspend fun getUsersStatistics(
        @Query("cityIds") cityIds: List<Int>? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null,
        @Query("positions") positions: List<String>? = null
    ): Response<ApiResponse<UserStatsResponse>>

    @GET("admin/statistics/ads")
    suspend fun getAdsStatistics(
        @Query("cityIds") cityIds: List<Int>? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null,
        @Query("statuses") statuses: List<String>? = null
    ): Response<ApiResponse<AdStatsResponse>>

    // ========== Реклама ==========
    @GET("advertisements/active")
    suspend fun getActiveAdvertisements(
        @Query("type") type: Int,
        @Query("cityId") cityId: Int,
        @Query("limit") limit: Int? = null
    ): Response<ApiResponse<List<Advertising>>>

    @GET("advertisements/{id}")
    suspend fun getAdvertisingById(@Path("id") id: String): Response<ApiResponse<Advertising>>

    @GET("advertisements")
    suspend fun getAdminAdvertisements(
        @Query("status") status: List<String>? = null,
        @Query("advertiser") advertiser: String? = null,
        @Query("cityIds") cityIds: List<Int>? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null,
        @Query("endDateFrom") endDateFrom: String? = null,
        @Query("endDateTo") endDateTo: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("sort") sort: String? = null
    ): Response<ApiResponse<AdvertisingListResponse>>

    @POST("advertisements")
    suspend fun createAdvertising(@Body advertising: Advertising): Response<ApiResponse<Advertising>>

    @PUT("advertisements/{id}")
    suspend fun updateAdvertising(
        @Path("id") id: String,
        @Body advertising: Advertising
    ): Response<ApiResponse<Advertising>>

    @DELETE("advertisements/{id}")
    suspend fun deleteAdvertising(@Path("id") id: String): Response<ApiResponse<Void>>

    @PATCH("advertisements/{id}/status")
    suspend fun updateAdvertisingStatus(
        @Path("id") id: String,
        @Body statusRequest: Map<String, String>
    ): Response<ApiResponse<Advertising>>

    @Multipart
    @POST("advertisements/upload-image")
    suspend fun uploadAdvertisingImage(@Part file: MultipartBody.Part): Response<ApiResponse<UploadImageResponse>>

    @GET("advertisements/statistics")
    suspend fun getAdvertisingStatistics(
        @Query("cityIds") cityIds: List<Int>? = null,
        @Query("type") type: Int? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null
    ): Response<ApiResponse<AdvertisingStatistics>>

}

package com.katok.pro.repository

import com.katok.pro.model.*
import com.katok.pro.network.ApiClient
import com.katok.pro.network.ApiService
import com.katok.pro.network.safeApiCall
import com.katok.pro.network.safeApiCallIgnoreNullData

class AdminRepository {

    private val apiService: ApiService
        get() = ApiClient.getApiService()

    // ========== Старые методы (для совместимости) ==========
    // Удаляем метод getUsers(page, size) – он не нужен, так как использует несуществующий эндпоинт
    // Вместо него используем getAdminUsers с параметрами

    suspend fun changeUserRole(userId: String, role: String): NetworkResult<Void> {
        val request = RoleChangeRequest(role)
        return safeApiCall { apiService.changeUserRole(userId, request) }
    }

    suspend fun changeUserStatus(userId: String, isActive: Boolean): NetworkResult<Void> {
        val request = StatusChangeRequest(isActive)
        return safeApiCall { apiService.changeUserStatus(userId, request) }
    }

    suspend fun deleteUser(userId: String): NetworkResult<Unit> {
        return safeApiCallIgnoreNullData { apiService.deleteUser(userId) }
    }

    suspend fun deleteAd(adId: String): NetworkResult<Void> {
        return safeApiCall { apiService.deleteAd(adId) }
    }

    suspend fun getModerationAds(page: Int, size: Int): NetworkResult<AdListResponse> {
        return safeApiCall { apiService.getModerationAds(page, size) }
    }

    suspend fun approveAd(adId: String): NetworkResult<Void> {
        return safeApiCall { apiService.approveAd(adId) }
    }

    suspend fun rejectAd(adId: String): NetworkResult<Void> {
        return safeApiCall { apiService.rejectAd(adId) }
    }

    suspend fun searchUsers(query: String, page: Int = 0, size: Int = 10): NetworkResult<UserListResponse> {
        return safeApiCall { apiService.searchUsers(query, page, size) }
    }

    suspend fun getTeams(): NetworkResult<List<String>> {
        return safeApiCall { apiService.getTeams() }
    }

    // ========== НОВЫЕ МЕТОДЫ С ПОДДЕРЖКОЙ СПИСКОВ ==========

    /**
     * Получение списка пользователей с фильтрами (множественный выбор)
     * Все параметры – списки, которые превращаются в повторяющиеся query-параметры
     */
    suspend fun getAdminUsers(
        role: List<String>? = null,
        status: List<String>? = null,
        cityId: List<Int>? = null,
        team: List<String>? = null,
        search: String? = null,
        page: Int = 0,
        size: Int = 20,
        sort: String? = "registeredAt,desc"
    ): NetworkResult<UserListResponse> {
        return safeApiCall {
            apiService.getAdminUsers(role, status, cityId, team, search, page, size, sort)
        }
    }

    /**
     * Получение пользователя по ID (админ)
     */
    suspend fun getUserById(userId: String): NetworkResult<User> {
        return safeApiCall { apiService.getAdminUserById(userId) }
    }

    /**
     * Обновление профиля пользователя (админ)
     */
    suspend fun updateUserProfile(userId: String, profile: Profile): NetworkResult<User> {
        return safeApiCall { apiService.updateAdminUserProfile(userId, profile) }
    }

    /**
     * Смена статуса (блокировка/разблокировка) – принимает строку
     */
    suspend fun changeUserStatus(userId: String, status: String): NetworkResult<User> {
        return safeApiCall { apiService.changeAdminUserStatus(userId, status) }
    }

    /**
     * Получение всех городов (упрощённый список)
     */
    suspend fun getAllCities(): NetworkResult<List<City>> {
        return safeApiCall { apiService.getAllCities() }
    }

    // ========== Административные объявления ==========

    /**
     * Получение списка объявлений с фильтрами (множественный выбор)
     */
    suspend fun getAdminAds(
        status: List<String>? = null,
        type: List<Int>? = null,
        subType: List<Int>? = null,
        cityId: List<Int>? = null,
        rinkId: List<Int>? = null,
        authorId: List<String>? = null,
        level: List<String>? = null,
        search: String? = null,
        page: Int = 0,
        size: Int = 20,
        sort: String? = "createdAt,desc"
    ): NetworkResult<AdListResponse> {
        return safeApiCall {
            apiService.getAdminAds(status, type, subType, cityId, rinkId, authorId, level, search, page, size, sort)
        }
    }

    /**
     * Получение всех стадионов (админ)
     */
    suspend fun getAdminRinks(): NetworkResult<List<Rink>> {
        return safeApiCall { apiService.getAdminRinks() }
    }

    // Удаляем старый метод getUsers(page, size) – он больше не используется
}
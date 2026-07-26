package com.katok.pro.repository

import com.katok.pro.model.Advertising
import com.katok.pro.model.AdListResponse
import com.katok.pro.model.AdvertisingListResponse
import com.katok.pro.model.AdvertisingStatistics
import com.katok.pro.model.NetworkResult
import com.katok.pro.model.UploadImageResponse
import com.katok.pro.network.ApiClient
import com.katok.pro.network.ApiService
import com.katok.pro.network.safeApiCall
import com.katok.pro.network.safeApiCallIgnoreNullData
import com.katok.pro.network.safeApiCallWithNullableData
import okhttp3.MultipartBody

class AdvertisingRepository {

    private val apiService: ApiService
        get() = ApiClient.getApiService()

    suspend fun getActiveAdvertisements(type: Int, cityId: Int, limit: Int? = null): NetworkResult<List<Advertising>> {
        return safeApiCall { apiService.getActiveAdvertisements(type, cityId, limit) }
    }

    suspend fun getAdminAdvertisements(
        status: List<String>? = null,
        advertiser: String? = null,
        cityIds: List<Int>? = null,
        dateFrom: String? = null,
        dateTo: String? = null,
        endDateFrom: String? = null,
        endDateTo: String? = null,
        page: Int = 0,
        size: Int = 20,
        sort: String? = "createdAt,desc"
    ): NetworkResult<AdvertisingListResponse> {
        return safeApiCall { apiService.getAdminAdvertisements(status, advertiser, cityIds, dateFrom, dateTo, endDateFrom, endDateTo, page, size, sort) }
    }

    suspend fun createAdvertising(advertising: Advertising): NetworkResult<Advertising> {
        return safeApiCall { apiService.createAdvertising(advertising) }
    }

    suspend fun updateAdvertising(id: String, advertising: Advertising): NetworkResult<Advertising> {
        return safeApiCall { apiService.updateAdvertising(id, advertising) }
    }

    suspend fun deleteAdvertising(id: String): NetworkResult<Unit> {
        // Используем safeApiCallIgnoreNullData для Void -> Unit
        return safeApiCallIgnoreNullData { apiService.deleteAdvertising(id) }
    }

    suspend fun updateAdvertisingStatus(id: String, status: String): NetworkResult<Advertising?> {
        val body = mapOf("status" to status)
        return safeApiCallWithNullableData { apiService.updateAdvertisingStatus(id, body) }
    }

    suspend fun uploadAdvertisingImage(filePart: MultipartBody.Part): NetworkResult<String> {
        val result = safeApiCall<UploadImageResponse> { apiService.uploadAdvertisingImage(filePart) }
        return when (result) {
            is NetworkResult.Success -> {
                val imageUrl = result.data?.imageUrl
                if (imageUrl != null) {
                    NetworkResult.Success(imageUrl)
                } else {
                    NetworkResult.Error("Ссылка на изображение не получена")
                }
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.Loading -> NetworkResult.Loading(result.isLoading)
        }
    }

    suspend fun getAdvertisingStatistics(
        cityIds: List<Int>? = null,
        type: Int? = null,
        dateFrom: String? = null,
        dateTo: String? = null
    ): NetworkResult<AdvertisingStatistics> {
        return safeApiCall { apiService.getAdvertisingStatistics(cityIds, type, dateFrom, dateTo) }
    }

    suspend fun getAdvertisingById(id: String): NetworkResult<Advertising> {
        return safeApiCall { apiService.getAdvertisingById(id) }
    }
}
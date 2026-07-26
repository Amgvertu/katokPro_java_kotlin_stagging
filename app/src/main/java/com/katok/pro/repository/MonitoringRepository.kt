package com.katok.pro.repository

import com.katok.pro.model.AdStatsResponse
import com.katok.pro.model.NetworkResult
import com.katok.pro.model.UserStatsResponse
import com.katok.pro.network.ApiClient
import com.katok.pro.network.ApiService
import com.katok.pro.network.safeApiCall

class MonitoringRepository {

    private val apiService: ApiService
        get() = ApiClient.getApiService()

    suspend fun getUsersStatistics(
        cityIds: List<Int>? = null,
        dateFrom: String? = null,
        dateTo: String? = null,
        positions: List<String>? = null
    ): NetworkResult<UserStatsResponse> {
        return safeApiCall {
            apiService.getUsersStatistics(cityIds, dateFrom, dateTo, positions)
        }
    }

    suspend fun getAdsStatistics(
        cityIds: List<Int>? = null,
        dateFrom: String? = null,
        dateTo: String? = null,
        statuses: List<String>? = null
    ): NetworkResult<AdStatsResponse> {
        return safeApiCall {
            apiService.getAdsStatistics(cityIds, dateFrom, dateTo, statuses)
        }
    }
}
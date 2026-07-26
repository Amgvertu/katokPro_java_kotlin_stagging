package com.katok.pro.repository

import android.util.Log
import com.katok.pro.model.*
import com.katok.pro.network.ApiClient
import com.katok.pro.network.ApiService
import com.katok.pro.network.safeApiCall
import com.katok.pro.network.safeApiCallIgnoreNullData
import okhttp3.MultipartBody

class AdRepository {

    private val apiService: ApiService
        get() = ApiClient.getApiService()

    suspend fun getAds(
        cityId: Int?,
        type: Int?,
        subType: Int?,
        level: List<String>?,
        page: Int,
        size: Int
    ): NetworkResult<AdListResponse> {
        val result = safeApiCall { apiService.getAds(cityId, type, subType, level, page, size) }
        if (result is NetworkResult.Success) {
            val sortedContent = result.data.content?.sortedWith(compareBy<Ad> { it.endTime == null }.thenBy { it.endTime })
            result.data.content = sortedContent
        }
        return result
    }

    suspend fun getMyAds(page: Int, size: Int): NetworkResult<AdListResponse> {
        val result = safeApiCall { apiService.getMyAds(page, size) }
        if (result is NetworkResult.Success) {
            result.data.content = sortAdsByEndTime(result.data.content)
        }
        return result
    }

    suspend fun getFilteredAds(
        cityId: Int?,
        type: Int?,
        subType: Int?,
        role: String?,
        levels: List<String>?,
        dateFrom: String?,
        dateTo: String?,
        timeFrom: String?,
        timeTo: String?,
        rinkIds: List<Int>?,
        page: Int,
        size: Int
    ): NetworkResult<AdListResponse> {
        val result = safeApiCall {
            apiService.getFilteredAds(
                cityId, type, subType, role, levels,
                dateFrom, dateTo, timeFrom, timeTo, rinkIds,
                page, size
            )
        }
        if (result is NetworkResult.Success) {
            result.data.content = sortAdsByEndTime(result.data.content)
        }
        return result
    }

    suspend fun getAdById(id: String): NetworkResult<Ad> {
        val result = safeApiCall { apiService.getAdById(id) }
        if (result is NetworkResult.Success) {
            Log.d("DEBUG_RINKS", "getAdById($id) success: rinkIds=${result.data.rinkIds}, cityId=${result.data.cityId}")
        } else {
            Log.d("DEBUG_RINKS", "getAdById($id) failed: $result")
        }
        return result
    }

    suspend fun createAd(ad: Ad): NetworkResult<Ad> {
        return safeApiCall { apiService.createAd(ad) }
    }

    suspend fun updateAd(id: String, ad: Ad): NetworkResult<Ad> {
        return safeApiCall { apiService.updateAd(id, ad) }
    }

    suspend fun deleteAd(id: String): NetworkResult<Unit> {
        return safeApiCallIgnoreNullData { apiService.deleteAd(id) }
    }

    suspend fun createResponse(adId: String, message: String, role: String?): NetworkResult<Response> {
        val response = Response().apply {
            this.message = message
            responseRole = role
        }
        return safeApiCall { apiService.createResponse(adId, response) }
    }

    suspend fun updateResponseStatus(responseId: String, status: String): NetworkResult<Response> {
        return safeApiCall { apiService.updateResponseStatus(responseId, status) }
    }

    suspend fun deleteResponse(responseId: String): NetworkResult<Unit> {
        return safeApiCallIgnoreNullData { apiService.deleteResponse(responseId) }
    }

    suspend fun getRinksByCity(cityId: Int): NetworkResult<List<Rink>> {
        return safeApiCall { apiService.getRinksByCity(cityId) }
    }

    suspend fun checkDuplicate(ad: Ad): NetworkResult<List<DuplicateAd>> {
        return safeApiCall { apiService.checkDuplicate(ad) }
    }

    suspend fun getMyResponses(page: Int, size: Int): NetworkResult<MyResponsesListResponse> {
        val result = safeApiCall { apiService.getMyResponses(page, size) }
        if (result is NetworkResult.Success) {
            result.data.content = sortWrappersByAdEndTime(result.data.content)
        }
        return result
    }

    suspend fun getAllActiveAds(type: Int?, subType: Int?, level: List<String>?, page: Int, size: Int): NetworkResult<AdListResponse> {
        return safeApiCall { apiService.getAllActiveAds(type, subType, level, page, size) }
    }

    suspend fun uploadAdPhoto(adId: String, file: MultipartBody.Part): NetworkResult<String> {
        return safeApiCall { apiService.uploadAdPhoto(adId, file) }
    }

    private fun sortAdsByEndTime(ads: List<Ad>?): List<Ad>? {
        return ads?.sortedWith(compareBy<Ad> { it.endTime == null }.thenBy { it.endTime })
    }

    private fun sortWrappersByAdEndTime(wrappers: List<MyResponseAdWrapper>?): List<MyResponseAdWrapper>? {
        return wrappers?.sortedWith(compareBy<MyResponseAdWrapper> { it.ad?.endTime == null }.thenBy { it.ad?.endTime })
    }
}
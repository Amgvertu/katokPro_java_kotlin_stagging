package com.katok.pro.repository

import android.content.Context
import com.katok.pro.model.*
import com.katok.pro.network.ApiClient
import com.katok.pro.network.ApiService
import com.katok.pro.network.safeApiCall
import retrofit2.Response
import kotlinx.coroutines.flow.first

class LocationRepository(
    context: Context,
    private val localDataRepository: LocalDataRepository
) {

    private val apiService: ApiService
        get() = ApiClient.getApiService()

    suspend fun getCountries(): NetworkResult<List<Country>> {
        return safeApiCall { apiService.getCountries() }
    }

    suspend fun getRegions(countryId: Int): NetworkResult<List<Region>> {
        return safeApiCall { apiService.getRegions(countryId) }
    }

    suspend fun getCities(regionId: Int): NetworkResult<List<City>> {
        return safeApiCall { apiService.getCities(regionId) }
    }

    suspend fun getAllCitiesByCountry(countryId: Int): NetworkResult<List<City>> {
        val cached = try {
            localDataRepository.getAllCities().first()
        } catch (e: Exception) {
            emptyList()
        }
        if (cached.isNotEmpty()) {
            return NetworkResult.Success(cached)
        }
        val result = safeApiCall { apiService.getAllCitiesByCountry(countryId) }
        if (result is NetworkResult.Success && result.data.isNotEmpty()) {
            localDataRepository.saveCities(result.data)
        }
        return result
    }

    suspend fun getRinksByCity(cityId: Int): NetworkResult<List<Rink>> {
        val cached = try {
            localDataRepository.getRinksByCity(cityId).first()
        } catch (e: Exception) {
            emptyList()
        }
        if (cached.isNotEmpty()) {
            return NetworkResult.Success(cached)
        }
        val result = safeApiCall { apiService.getRinksByCity(cityId) }
        if (result is NetworkResult.Success && result.data.isNotEmpty()) {
            localDataRepository.saveRinks(result.data)
        }
        return result
    }
}
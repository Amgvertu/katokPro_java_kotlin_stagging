package com.katok.pro.util

import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.katok.pro.KatokApplication
import com.katok.pro.model.City
import com.katok.pro.model.NetworkResult
import com.katok.pro.repository.LocationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.IOException

class CityDetector(
    private val context: Context,
    private val locationRepository: LocationRepository   // ← добавить
) {

    companion object {
        private const val COUNTRY_RUSSIA_ID = 1
    }

    fun detectCity(location: Location): Flow<CityDetectResult> = flow {
        emit(CityDetectResult.Loading)
        val result = withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context)
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                if (addresses != null && addresses.isNotEmpty()) {
                    val address = addresses[0]
                    var cityName = address.locality
                    if (cityName == null) cityName = address.subAdminArea
                    if (cityName == null) cityName = address.adminArea
                    if (cityName != null) {
                        findCityByName(cityName)
                    } else {
                        CityDetectResult.Error("Город не определён")
                    }
                } else {
                    CityDetectResult.Error("Адрес не найден")
                }
            } catch (e: IOException) {
                CityDetectResult.Error("Ошибка геокодирования: ${e.message}")
            } catch (e: SecurityException) {
                CityDetectResult.Error("Нет разрешения на определение местоположения")
            }
        }
        emit(result)
    }

    private suspend fun findCityByName(cityName: String): CityDetectResult {
        val result = locationRepository.getAllCitiesByCountry(COUNTRY_RUSSIA_ID)
        return when (result) {
            is NetworkResult.Success -> {
                val city = result.data.find { it.name.equals(cityName, ignoreCase = true) }
                if (city != null) {
                    CityDetectResult.Success(city)
                } else {
                    CityDetectResult.Error("Город не найден в базе: $cityName")
                }
            }
            is NetworkResult.Error -> {
                CityDetectResult.Error("Ошибка загрузки списка городов: ${result.message}")
            }
            else -> {
                CityDetectResult.Error("Ошибка загрузки списка городов")
            }
        }
    }
}

sealed class CityDetectResult {
    object Loading : CityDetectResult()
    data class Success(val city: City) : CityDetectResult()
    data class Error(val message: String) : CityDetectResult()
}
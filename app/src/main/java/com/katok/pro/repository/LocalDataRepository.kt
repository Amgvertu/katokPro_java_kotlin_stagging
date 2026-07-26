package com.katok.pro.repository

import android.content.Context
import com.katok.pro.database.AppDatabase
import com.katok.pro.database.entities.CityEntity
import com.katok.pro.database.entities.RinkEntity
import com.katok.pro.model.City
import com.katok.pro.model.Rink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LocalDataRepository(context: Context) {
    private val database = AppDatabase.getInstance(context)

    fun getAllCities(): Flow<List<City>> {
        return database.cityDao().getAll().map { entities ->
            entities.map { it.toModel() }
        }
    }

    suspend fun saveCities(cities: List<City>) = withContext(Dispatchers.IO) {
        val entities = cities.map { CityEntity.fromModel(it) }
        database.cityDao().insertAll(entities)
    }

    suspend fun clearCities() = withContext(Dispatchers.IO) {
        database.cityDao().deleteAll()
    }

    fun getRinksByCity(cityId: Int): Flow<List<Rink>> {
        return database.rinkDao().getByCityId(cityId).map { entities ->
            entities.map { it.toModel() }
        }
    }

    suspend fun saveRinks(rinks: List<Rink>) = withContext(Dispatchers.IO) {
        if (rinks.isEmpty()) return@withContext
        val cityId = rinks.first().city?.id ?: return@withContext
        database.rinkDao().deleteByCityId(cityId)
        val entities = rinks.map { RinkEntity.fromModel(it) }
        database.rinkDao().insertAll(entities)
    }
}
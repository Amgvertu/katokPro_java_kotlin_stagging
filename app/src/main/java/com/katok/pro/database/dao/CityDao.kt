package com.katok.pro.database.dao

import androidx.room.*
import com.katok.pro.database.entities.CityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CityDao {
    @Query("SELECT * FROM cities ORDER BY name")
    fun getAll(): Flow<List<CityEntity>>

    @Query("SELECT * FROM cities WHERE id = :cityId")
    fun getById(cityId: Int): CityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(cities: List<CityEntity>)

    @Query("DELETE FROM cities")
    fun deleteAll()
}
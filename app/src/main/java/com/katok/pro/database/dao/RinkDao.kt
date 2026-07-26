package com.katok.pro.database.dao

import androidx.room.*
import com.katok.pro.database.entities.RinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RinkDao {
    @Query("SELECT * FROM rinks WHERE cityId = :cityId")
    fun getByCityId(cityId: Int): Flow<List<RinkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(rinks: List<RinkEntity>)

    @Query("DELETE FROM rinks WHERE cityId = :cityId")
    fun deleteByCityId(cityId: Int)
}
package com.katok.pro.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.katok.pro.database.dao.AdminMessageDao
import com.katok.pro.database.dao.CityDao
import com.katok.pro.database.dao.RinkDao
import com.katok.pro.database.entities.CityEntity
import com.katok.pro.database.entities.RinkEntity
import com.katok.pro.model.admin.AdminMessage

@Database(
    entities = [CityEntity::class, RinkEntity::class, AdminMessage::class],
    version = 2, // увеличиваем версию, так как добавляем таблицу
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cityDao(): CityDao
    abstract fun rinkDao(): RinkDao
    abstract fun adminMessageDao(): AdminMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "katok_database"
                ).fallbackToDestructiveMigration()
                        .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

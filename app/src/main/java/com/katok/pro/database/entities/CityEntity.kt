package com.katok.pro.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.katok.pro.model.City

@Entity(tableName = "cities")
data class CityEntity(
    @PrimaryKey
    val id: Int,
    val name: String,
    val regionId: Int?,
    val regionName: String?
) {
    fun toModel(): City {
        val region = if (regionId != null && regionName != null) {
            com.katok.pro.model.Region(regionId, regionName)
        } else null
        return City(id, name, region)
    }

    companion object {
        fun fromModel(city: City): CityEntity {
            return CityEntity(
                id = city.id,
                name = city.name ?: "",
                regionId = city.region?.id,
                regionName = city.region?.name
            )
        }
    }
}
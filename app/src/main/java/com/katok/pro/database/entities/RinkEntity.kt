package com.katok.pro.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.katok.pro.model.City
import com.katok.pro.model.Rink

@Entity(tableName = "rinks")
data class RinkEntity(
    @PrimaryKey
    val id: Int,
    val name: String,
    val cityId: Int,
    val cityName: String?,
    val address: String?,
    val phone: String?,
    val rating: Double?
) {
    fun toModel(city: City? = null): Rink {
        return Rink(
            id = id,
            name = name,
            city = city ?: com.katok.pro.model.City(cityId, cityName, null),
            address = address,
            phone = phone,
            rating = rating
        )
    }

    companion object {
        fun fromModel(rink: Rink): RinkEntity {
            return RinkEntity(
                id = rink.id,
                name = rink.name ?: "",
                cityId = rink.city?.id ?: 0,
                cityName = rink.city?.name,
                address = rink.address,
                phone = rink.phone,
                rating = rink.rating
            )
        }
    }
}
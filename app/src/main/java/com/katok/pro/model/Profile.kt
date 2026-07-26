package com.katok.pro.model

import com.google.gson.annotations.SerializedName

class Profile {
    @SerializedName("id")
    var id: String? = null

    @SerializedName("firstName")
    var firstName: String? = null

    @SerializedName("lastName")
    var lastName: String? = null

    @SerializedName("birthDate")
    var birthDate: String? = null

    @SerializedName("position")
    var position: String? = null

    @SerializedName("level")
    var level: String? = null

    @SerializedName("number")
    var number: Int? = null

    @SerializedName("team")
    var team: String? = null

    @SerializedName("email")
    var email: String? = null

    @SerializedName("avatarUrl")
    var avatarUrl: String? = null

    @SerializedName("homeCountryId")
    var homeCountryId: Int? = null

    @SerializedName("homeRegionId")
    var homeRegionId: Int? = null

    @SerializedName("homeCityId")
    var homeCityId: Int? = null

    // Для обратной совместимости - получаем из API
    @SerializedName("homeCity")
    var homeCity: City? = null
        set(value) {
            field = value
            if (value != null) {
                this.homeCityId = value.id
            }
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Profile) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }
}

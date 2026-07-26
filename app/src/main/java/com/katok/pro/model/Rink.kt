package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class Rink(
    @SerializedName("id")
    var id: Int = 0,

    @SerializedName("name")
    var name: String? = null,

    @SerializedName("city")
    var city: City? = null,

    @SerializedName("address")
    var address: String? = null,

    @SerializedName("phone")
    var phone: String? = null,

    @SerializedName("rating")
    var rating: Double? = null
)

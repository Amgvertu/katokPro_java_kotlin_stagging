package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class City(
    @SerializedName("id")
    var id: Int = 0,

    @SerializedName("name")
    var name: String? = null,

    @SerializedName("region")
    var region: Region? = null
)

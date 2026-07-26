package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class Country(
    @SerializedName("id")
    var id: Int = 0,

    @SerializedName("name")
    var name: String? = null,

    @SerializedName("code")
    var code: String? = null,

    @SerializedName("phoneCode")
    var phoneCode: String? = null
)

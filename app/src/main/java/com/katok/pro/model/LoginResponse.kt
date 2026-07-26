package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("accessToken")
    var accessToken: String? = null,

    @SerializedName("refreshToken")
    var refreshToken: String? = null,

    @SerializedName("user")
    var user: User? = null
)

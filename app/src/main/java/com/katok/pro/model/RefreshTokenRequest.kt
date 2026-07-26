package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class RefreshTokenRequest(
    @SerializedName("refreshToken")
    var refreshToken: String? = null
)

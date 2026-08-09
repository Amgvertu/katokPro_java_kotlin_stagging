package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class PushTokenRequest(
    @SerializedName("token")
    val token: String,
    @SerializedName("platform")
    val platform: String // "FCM" или "HMS"
)
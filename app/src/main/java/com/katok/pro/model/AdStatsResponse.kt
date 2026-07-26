package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class AdStatsResponse(
    @SerializedName("currentAds")
    val currentAds: Int,

    @SerializedName("currentResponses")
    val currentResponses: Int,

    @SerializedName("currentAccepted")
    val currentAccepted: Int,

    @SerializedName("cumulativeAds")
    val cumulativeAds: Int,

    @SerializedName("cumulativeResponses")
    val cumulativeResponses: Int,

    @SerializedName("cumulativeAccepted")
    val cumulativeAccepted: Int
)
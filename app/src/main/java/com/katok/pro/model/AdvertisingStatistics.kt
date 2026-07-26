package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class AdvertisingStatistics(
    @SerializedName("total")
    val total: Int = 0,
    @SerializedName("byType")
    val byType: Map<Int, Int>? = null,
    @SerializedName("byCity")
    val byCity: Map<Int, Int>? = null
)
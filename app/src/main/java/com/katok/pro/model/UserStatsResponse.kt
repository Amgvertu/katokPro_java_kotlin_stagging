package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class UserStatsResponse(
    @SerializedName("currentUsers")
    val currentUsers: Int,

    @SerializedName("cumulativeUsers")
    val cumulativeUsers: Int
)
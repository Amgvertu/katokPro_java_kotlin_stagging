package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class StatusChangeRequest(
    @SerializedName("isActive")
    val isActive: Boolean
)

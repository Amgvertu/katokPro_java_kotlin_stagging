package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class Agreement(
    @SerializedName("type")
    val type: String? = null,

    @SerializedName("content")
    val content: String? = null,

    @SerializedName("updatedAt")
    val updatedAt: String? = null
)
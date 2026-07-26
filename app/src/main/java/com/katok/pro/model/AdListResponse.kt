package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class AdListResponse(
    @SerializedName("totalPages")
    var totalPages: Int = 0,

    @SerializedName("totalElements")
    var totalElements: Long = 0,

    @SerializedName("size")
    var size: Int = 0,

    @SerializedName("content")
    var content: List<Ad>? = null,

    @SerializedName("number")
    var number: Int = 0,

    @SerializedName("numberOfElements")
    var numberOfElements: Int = 0
)

package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class AdvertisingListResponse(
    @SerializedName("content")
    val content: List<Advertising>? = null,

    @SerializedName("totalPages")
    val totalPages: Int = 0,

    @SerializedName("totalElements")
    val totalElements: Long = 0,

    @SerializedName("size")
    val size: Int = 0,

    @SerializedName("number")
    val number: Int = 0,

    @SerializedName("numberOfElements")
    val numberOfElements: Int = 0,

    @SerializedName("first")
    val first: Boolean = false,

    @SerializedName("last")
    val last: Boolean = false,

    @SerializedName("empty")
    val empty: Boolean = false
)
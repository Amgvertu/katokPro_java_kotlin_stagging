package com.katok.pro.model.admin

import com.google.gson.annotations.SerializedName

data class AdminMessageListResponse(
    @SerializedName("content")
    val content: List<AdminMessage>? = null,

    @SerializedName("totalPages")
    val totalPages: Int = 0,

    @SerializedName("totalElements")
    val totalElements: Long = 0,

    @SerializedName("size")
    val size: Int = 0,

    @SerializedName("number")
    val number: Int = 0
)
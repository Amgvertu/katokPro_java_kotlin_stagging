package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class UserListResponse(
    @SerializedName("totalPages")
    var totalPages: Int = 0,

    @SerializedName("totalElements")
    var totalElements: Long = 0,

    @SerializedName("size")
    var size: Int = 0,

    @SerializedName("content")
    var content: List<User>? = null,

    @SerializedName("number")
    var number: Int = 0
)

package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    @SerializedName("success")
    var isSuccess: Boolean = false,

    @SerializedName("message")
    var message: String? = null,

    @SerializedName("data")
    var data: T? = null
)

package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class Response(
    @SerializedName("id")
    var id: String? = null,

    @SerializedName("adId")
    var adId: String? = null,

    @SerializedName("userId")
    var userId: String? = null,

    @SerializedName("user")
    var user: User? = null,

    @SerializedName("status")
    var status: String? = null,

    @SerializedName("message")
    var message: String? = null,

    @SerializedName("createdAt")
    var createdAt: String? = null,

    @SerializedName("responseRole")
    var responseRole: String? = null
)

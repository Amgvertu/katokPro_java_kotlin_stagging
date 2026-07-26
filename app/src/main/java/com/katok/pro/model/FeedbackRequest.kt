package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class FeedbackRequest(
    @SerializedName("fullName") var fullName: String? = null,
    @SerializedName("phone") var phone: String? = null,
    @SerializedName("email") var email: String? = null,
    @SerializedName("subject") var subject: String? = null,
    @SerializedName("message") var message: String? = null
)
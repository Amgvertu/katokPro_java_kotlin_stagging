package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class ChangePasswordRequest(
    @SerializedName("oldPassword")
    var oldPassword: String? = null,

    @SerializedName("newPassword")
    var newPassword: String? = null
)

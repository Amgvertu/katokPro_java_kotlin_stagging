package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class RoleChangeRequest(
    @SerializedName("role")
    var role: String? = null
)

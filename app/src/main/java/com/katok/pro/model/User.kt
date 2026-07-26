package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class User(
    @SerializedName("id")
    var id: String? = null,

    @SerializedName("phone")
    var phone: String? = null,

    @SerializedName("role")
    var role: String? = null,

    @SerializedName("subrole")
    var subrole: String? = null,

    @SerializedName("status")
    var status: String? = null,

    @SerializedName("isActive")
    var isActive: Boolean = false,

    @SerializedName("registeredAt")
    var registeredAt: String? = null,

    @SerializedName("lastLoginAt")
    var lastLoginAt: String? = null,

    @SerializedName("profile")
    var profile: Profile? = null
)

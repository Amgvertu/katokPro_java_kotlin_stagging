package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class Advertising(
    @SerializedName("id")
    var id: String? = null,

    @SerializedName("advertiser")
    var advertiser: String? = null,

    @SerializedName("imageUrl")
    var imageUrl: String? = null,

    @SerializedName("link")
    var link: String? = null,

    @SerializedName("refData")
    var refData: String? = null,

    @SerializedName("type")
    var type: Int = 1, // 1 - лента, 2 - диалог

    @SerializedName("interval")
    var interval: Int? = null, // только для type=1

    @SerializedName("periodDays")
    var periodDays: Int = 30,

    @SerializedName("cityIds")
    var cityIds: List<Int>? = null,

    @SerializedName("allCities")
    var allCities: Boolean = false,

    @SerializedName("status")
    var status: String? = null, // ACTIVE, PAUSED, EXPIRED, DELETED

    @SerializedName("startDate")
    var startDate: String? = null,

    @SerializedName("endDate")
    var endDate: String? = null,

    @SerializedName("createdAt")
    var createdAt: String? = null,

    @SerializedName("updatedAt")
    var updatedAt: String? = null
)
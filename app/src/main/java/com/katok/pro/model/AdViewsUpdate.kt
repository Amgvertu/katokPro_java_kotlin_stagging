package com.katok.pro.model

import com.google.gson.annotations.SerializedName

/**
 * Полезная нагрузка события AD_VIEWS_UPDATED, приходящего по WebSocket.
 */
data class AdViewsUpdate(
    @SerializedName("adId")
    val adId: String,

    @SerializedName("viewsCount")
    val viewsCount: Long
)
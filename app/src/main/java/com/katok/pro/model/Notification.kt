package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class Notification(
    @SerializedName("id")
    var id: Long = 0,

    @SerializedName("type")
    var type: String? = null,

    @SerializedName("content")
    var content: String? = null,

    @SerializedName("relatedEntityId")
    var relatedEntityId: String? = null,

    @SerializedName("read")
    var isRead: Boolean = false,

    @SerializedName("createdAt")
    var createdAt: String? = null  // ← изменили с Date на String
)

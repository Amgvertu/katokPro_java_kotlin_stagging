package com.katok.pro.model.admin

import com.google.gson.annotations.SerializedName
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "admin_messages_local")
data class AdminMessage(
    @PrimaryKey
    @SerializedName("id")
    val id: Long,

    @SerializedName("title")
    val title: String? = null,

    @SerializedName("content")
    val content: String,

    @SerializedName("imageUrl")
    val imageUrl: String? = null,

    @SerializedName("link")
    val link: String? = null,

    @SerializedName("category")
    val category: String, // "INTERNAL" или "PUSH"

    @SerializedName("createdAt")
    val createdAt: String,

    @SerializedName("isRead")
    var isRead: Boolean = false
)
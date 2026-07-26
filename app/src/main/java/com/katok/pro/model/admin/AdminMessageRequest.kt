package com.katok.pro.model.admin

import com.google.gson.annotations.SerializedName

data class AdminMessageRequest(
    @SerializedName("title") val title: String? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("link") val link: String? = null,
    @SerializedName("category") val category: String,
    @SerializedName("delivery") val delivery: DeliveryCriteria
) {
    data class DeliveryCriteria(
        @SerializedName("allUsers") val allUsers: Boolean = false,
        @SerializedName("admins") val admins: Boolean = false,
        @SerializedName("moderators") val moderators: Boolean = false,
        @SerializedName("allCities") val allCities: Boolean = false,
        @SerializedName("cityIds") val cityIds: List<Int>? = null,
        @SerializedName("allTeams") val allTeams: Boolean = false,
        @SerializedName("teamNames") val teamNames: List<String>? = null,
        @SerializedName("userIds") val userIds: List<String>? = null
    )
}
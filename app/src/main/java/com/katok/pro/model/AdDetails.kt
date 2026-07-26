package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class AdDetails(
    @SerializedName("role")
    var role: String? = null, // "вратарь" или "полевой"

    @SerializedName("countPlayers")
    var countPlayers: Int? = null, // количество игроков

    @SerializedName("delivery")
    var delivery: String? = null, // "yes"/"no"

    @SerializedName("payment")
    var payment: String? = null, // оплата

    @SerializedName("endTime")
    var endTime: String? = null, // время окончания

    @SerializedName("team")
    var team: String? = null, // команда соперника

    @SerializedName("specialists")
    var specialists: List<String>? = null // список специалистов
) {
    constructor(
        role: String,
        countPlayers: Int?,
        defenders: Int?,
        forwards: Int?,
        delivery: String,
        payment: String,
        endTime: String,
        team: String,
        specialists: List<String>?
    ) : this(role, countPlayers, delivery, payment, endTime, team, specialists)
}

package com.katok.pro.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.util.UUID

data class Ad(
    @SerializedName("id")
    var id: UUID? = null,

    @SerializedName("authorId")
    var authorId: String? = null,

    @SerializedName("author")
    var author: User? = null,

    @SerializedName("type")
    var type: Int = 0,

    @SerializedName("subType")
    var subType: Int = 0,

    @SerializedName("status")
    var status: String? = null,

    @SerializedName("startTime")
    var startTime: String? = null,

    @SerializedName("endTime")
    var endTime: String? = null,

    @SerializedName("level")
    var level: List<String>? = null,

    @SerializedName("city")
    var city: City? = null,

    @SerializedName("team")
    var team: String? = null,

    @SerializedName("showTeam")
    var showTeam: Boolean? = true,

    @SerializedName("contactName")
    var contactName: String? = null,

    @SerializedName("contactPhone")
    var contactPhone: String? = null,

    @SerializedName("rinkIds")
    var rinkIds: List<Int>? = null,

    @SerializedName("details")
    var details: AdDetails? = null,

    @SerializedName("responses")
    var responses: List<Response>? = null,

    @SerializedName("goaliesCount")
    var goaliesCount: Int? = null,

    @SerializedName("defendersCount")
    var defendersCount: Int? = null,

    @SerializedName("forwardsCount")
    var forwardsCount: Int? = null,

    @SerializedName("acceptedGoaliesCount")
    var acceptedGoaliesCount: Int? = null,

    @SerializedName("acceptedDefendersCount")
    var acceptedDefendersCount: Int? = null,

    @SerializedName("acceptedForwardsCount")
    var acceptedForwardsCount: Int? = null,

    @SerializedName("cityId")
    var cityId: Int? = null,

    // ДОБАВЛЕННЫЕ ПОЛЯ:
    @SerializedName("durationMinutes")
    var durationMinutes: Int? = null,

    @SerializedName("pricePerPlayer")
    var pricePerPlayer: Double? = null,

    @SerializedName("goalieCount")
    var goalieCount: Int? = null,

    @SerializedName("playerCount")
    var playerCount: Int? = null,

    @SerializedName("minPlayers")
    var minPlayers: Int? = null,

    @SerializedName("maxPlayers")
    var maxPlayers: Int? = null,

    @SerializedName("description")
    var description: String? = null,

    @Transient
    var isNew: Boolean = false
) : Serializable {

    fun getTagText(): String {
        val type = this.type
        val subType = this.subType

        if (type == 1 && subType == 1) return "НУЖЕН ВРАТАРЬ"
        if (type == 1 && subType == 2) return "НУЖЕН ПОЛЕВОЙ"
        if (type == 2 && subType == 1) return "ИЩУ ЛЕД (ВРАТАРЬ)"
        if (type == 2 && subType == 2) return "ИЩУ ЛЕД (ПОЛЕВОЙ)"
        if (type == 3 && subType == 1) return "ИЩУ ТОВАРИЩЕСКИЙ МАТЧ"
        if (type == 3 && subType == 2) return "ПРЕДЛАГАЮ ТОВАРИЩЕСКИЙ МАТЧ"
        if (type == 4) {
            when (subType) {
                1 -> return "НУЖЕН СУДЬЯ"
                2 -> return "НУЖЕН ФОТОГРАФ"
                3 -> return "НУЖЕН МЕДИК"
                4 -> return "НУЖЕН ТРЕНЕР"
            }
        }
        return ""
    }
}

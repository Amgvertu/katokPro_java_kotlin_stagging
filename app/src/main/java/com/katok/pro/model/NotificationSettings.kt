package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class NotificationSettings(
    @SerializedName("notifyOnResponseToMyAd")
    var isNotifyOnResponseToMyAd: Boolean = false,

    @SerializedName("notifyOnMyResponseAccepted")
    var isNotifyOnMyResponseAccepted: Boolean = false,

    @SerializedName("notifyNewAdsInCity")
    var isNotifyNewAdsInCity: Boolean = false,

    @SerializedName("notificationCityId")   // для отправки
    var notificationCityId: Int? = null,

    @SerializedName("notificationCity")     // для получения
    var notificationCity: City? = null,

    @SerializedName("subscriptions")
    var subscriptions: List<Subscription>? = null
) {
    data class Subscription(
        @SerializedName("type")
        var type: Int = 0,
        @SerializedName("subType")
        var subType: Int = 0
    ) {
        constructor() : this(0, 0)

        override fun equals(other: Any?): Boolean {
            if (other is Subscription) {
                return type == other.type && subType == other.subType
            }
            return false
        }

        override fun hashCode(): Int {
            return 31 * type + subType
        }
    }
}

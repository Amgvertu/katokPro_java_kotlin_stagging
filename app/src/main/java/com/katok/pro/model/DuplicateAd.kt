package com.katok.pro.model

import java.util.UUID

data class DuplicateAd(
    var id: UUID? = null,
    var startTime: String? = null,
    var rinkName: String? = null,
    var cityName: String? = null,
    var status: String? = null,
    var filledProgress: String? = null
)

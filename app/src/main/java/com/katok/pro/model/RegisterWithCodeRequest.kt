package com.katok.pro.model

data class RegisterWithCodeRequest(
    var phone: String? = null,
    var password: String? = null,
    var code: String? = null,
    var countryId: Int = 0,
    var regionId: Int = 0,
    var cityId: Int = 0
)

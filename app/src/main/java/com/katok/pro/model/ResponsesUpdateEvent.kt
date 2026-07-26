package com.katok.pro.model

data class ResponsesUpdateEvent(
    val adId: String,
    val responses: List<Response>
)

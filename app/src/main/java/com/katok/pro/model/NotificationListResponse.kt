package com.katok.pro.model

data class NotificationListResponse(
    var content: List<Notification>? = null,
    var totalPages: Int = 0,
    var totalElements: Long = 0,
    var size: Int = 0,
    var number: Int = 0,
    var isLast: Boolean = false
)

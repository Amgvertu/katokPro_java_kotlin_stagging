package com.katok.pro.model

data class MyResponsesListResponse(
    var content: List<MyResponseAdWrapper>? = null,
    var totalPages: Int = 0,
    var totalElements: Long = 0,
    var size: Int = 0,
    var number: Int = 0,
    var numberOfElements: Int = 0,
    var isFirst: Boolean = false,
    var isLast: Boolean = false,
    var isEmpty: Boolean = false
)

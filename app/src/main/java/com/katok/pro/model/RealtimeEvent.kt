package com.katok.pro.model

import androidx.annotation.Nullable

data class RealtimeEvent(
    val type: Type,
    val entityId: String?,  // было: String
    val action: String?,
    val payload: Any?
) {
    enum class Type {
        AD_CREATED,
        AD_UPDATED,
        AD_DELETED,
        RESPONSE_ADDED,
        RESPONSE_REMOVED,
        RESPONSE_APPROVED,
        RESPONSE_REJECTED,
        APPROVAL_CANCELLED,
        RESPONSE_WITHDRAWN
    }
}

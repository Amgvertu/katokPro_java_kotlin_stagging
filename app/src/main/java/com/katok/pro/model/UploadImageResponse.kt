package com.katok.pro.model

import com.google.gson.annotations.SerializedName

data class UploadImageResponse(
    @SerializedName("imageUrl")
    val imageUrl: String
)
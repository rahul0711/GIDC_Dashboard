package com.example.mygidc.model

import com.google.gson.annotations.SerializedName

data class UpdateComplainResponse(
    @SerializedName("success")
    val success: Boolean? = null,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("status")
    val status: String? = null
)
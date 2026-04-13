package com.example.mygidc.model

import com.google.gson.annotations.SerializedName

data class ApprovedResolvedComplainResponse(
    @SerializedName("message")
    val message: String? = null,

    @SerializedName("status")
    val status: String? = null,

    @SerializedName("success")
    val success: Boolean? = null
)
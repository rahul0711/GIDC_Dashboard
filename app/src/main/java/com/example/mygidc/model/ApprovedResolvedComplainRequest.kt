package com.example.mygidc.model

import com.google.gson.annotations.SerializedName

data class ApprovedResolvedComplainRequest(
    @SerializedName("ComplainFormID")
    val complainFormID: Int,

    @SerializedName("AgencyId")
    val agencyId: String,

    @SerializedName("Status")
    val status: String,

    @SerializedName("CallDuration")
    val callDuration: String,

    @SerializedName("ComplainType")
    val complainType: String,

    @SerializedName("CallStartTime")
    val callStartTime: String,

    @SerializedName("CallMobileNumber")
    val callMobileNumber: String,

    @SerializedName("CallRecordingLink")
    val callRecordingLink: String,

    @SerializedName("ComplainSpecialNotes")
    val complainSpecialNotes: String
)
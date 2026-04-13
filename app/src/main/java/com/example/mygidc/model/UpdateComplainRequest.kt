package com.example.mygidc.model

import com.google.gson.annotations.SerializedName

data class UpdateComplainRequest(
    @SerializedName("ComplainFormID")
    val complainFormID: Int,

    @SerializedName("AgencyId")
    val agencyId: String,

    @SerializedName("Status")
    val status: String,

    @SerializedName("CallDuration")
    val callDuration: String = "5 minutes",

    @SerializedName("ComplainType")
    val complainType: String = "Drainage",

    @SerializedName("CallStartTime")
    val callStartTime: String = "2023-04-06T10:00:00",

    @SerializedName("CallMobileNumber")
    val callMobileNumber: String = "9876543210",

    @SerializedName("CallRecordingLink")
    val callRecordingLink: String = "http://example.com/recording.mp3",

    @SerializedName("ComplainSpecialNotes")
    val complainSpecialNotes: String
)
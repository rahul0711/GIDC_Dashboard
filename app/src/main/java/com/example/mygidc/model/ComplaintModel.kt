package com.example.mygidc.model

import com.google.gson.annotations.SerializedName

data class ComplaintModel(

    @SerializedName("complainFormID")
    val complainFormID: Int = 0,

    @SerializedName("callMobileNumber")
    val callMobileNumber: String? = null,

    @SerializedName("callStartTime")
    val callStartTime: String? = null,

    @SerializedName("businessHours")
    val businessHours: String? = null,

    @SerializedName("callDuration")
    val callDuration: String? = null,

    @SerializedName("callRecordingLink")
    val callRecordingLink: String? = null,

    @SerializedName("complainType")
    val complainType: String? = null,

    @SerializedName("complainTypeId")
    val complainTypeId: String? = null,

    @SerializedName("complainSubType")
    val complainSubType: String? = null,

    @SerializedName("complainSubTypeID")
    val complainSubTypeID: String? = null,

    @SerializedName("complainArea")
    val complainArea: String? = null,

    @SerializedName("complainLandmark")
    val complainLandmark: String? = null,

    @SerializedName("agencyId")
    val agencyId: String? = null,

    @SerializedName("complainSpecialNotes")
    val complainSpecialNotes: String? = null,

    @SerializedName("complainPriority")
    val complainPriority: String? = null,

    @SerializedName("status")
    val status: String? = null,

    @SerializedName("approvedById")
    val approvedById: String? = null,

    @SerializedName("approvedBy")
    val approvedBy: String? = null,

    @SerializedName("approvedDate")
    val approvedDate: String? = null,

    @SerializedName("agency")
    val agency: String? = null,

    @SerializedName("resolveTime")
    val resolveTime: String? = null,

    @SerializedName("alertTime")
    val alertTime: String? = null,

    @SerializedName("departmentId")
    val departmentId: String? = null,

    @SerializedName("agencyPhoneno")
    val agencyPhoneno: String? = null
)
package com.example.mygidc.model

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("userID")                val userID: Long = 0,
    @SerializedName("userName")              val userName: String = "",
    @SerializedName("mobile")               val mobile: String? = null,
    @SerializedName("email")                val email: String? = null,
    @SerializedName("type")                 val type: String = "",
    @SerializedName("departmentId")         val departmentId: Int? = null,
    @SerializedName("designation")          val designation: String? = null,
    @SerializedName("password")             val password: String? = null,
    @SerializedName("notes")                val notes: String? = null,
    @SerializedName("status")              val status: String = "",
    @SerializedName("listDepartmentMaster") val listDepartmentMaster: List<Department>? = null,
    @SerializedName("departmentName")       val departmentName: String? = null  // ✅ nullable
)
package com.example.mygidc.model

import com.google.gson.annotations.SerializedName

data class DepartmentStats(
    @SerializedName("new")       val newCount: Int = 0,
    @SerializedName("inProcess") val inProcess: Int = 0,
    @SerializedName("hold")      val hold: Int = 0,
    @SerializedName("resolved")  val resolved: Int = 0,
    @SerializedName("cancel")    val cancel: Int = 0,
    @SerializedName("reLaunched")  val reLaunched: Int = 0,
    @SerializedName("approved")    val approved: Int = 0,
    @SerializedName("alertCount")  val alertCount: Int = 0,
    @SerializedName("resolveCount") val resolveCount: Int = 0  // ← make sure this exists
)
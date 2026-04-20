package com.example.mygidc.model

import com.google.gson.annotations.SerializedName

/**
 * Response model for:
 *   GET /API/MobileApp/GetDailyComplainCountDepartmentWise
 *   GET /API/MobileApp/GetDailyResolvedCountDepartmentWise
 *
 * Both return: { "count": 2 }
 */
data class DailyCountResponse(
    @SerializedName("count")
    val count: Int = 0
)
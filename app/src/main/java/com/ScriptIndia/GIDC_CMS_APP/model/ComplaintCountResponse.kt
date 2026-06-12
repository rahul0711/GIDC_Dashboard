package com.ScriptIndia.GIDC_CMS_APP.model

data class ComplaintCountResponse(
    val new: Int,
    val inProcess: Int,
    val hold: Int,
    val resolved: Int,
    val cancel: Int,
    val reLaunched: Int,
    val approved: Int,
    val alertCount: Int,
    val resolveCount: Int
)
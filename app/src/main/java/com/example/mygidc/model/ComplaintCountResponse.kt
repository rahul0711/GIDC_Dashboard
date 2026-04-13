package com.example.mygidc.model

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
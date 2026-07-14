package com.ScriptIndia.GIDC_CMS_APP.model

data class Department(
    val departmentId: Int,
    val departmentName: String,
    val departmentNotes: String?,
    val userMasters: Any?,
    val complainTypeMasters: Any?
)